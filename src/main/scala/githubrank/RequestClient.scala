package githubrank

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.headers.{Link, LinkValue}
import akka.http.scaladsl.model.{HttpHeader, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri, headers}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

object RequestClient{
  def props[A](host: String,  bufferSize: Int, overflowStrategy: OverflowStrategy, converter: HttpResponse => Future[Seq[A]]) : Props =
    Props(new RequestClient[A](host, bufferSize, overflowStrategy, converter))
}

class RequestClient[A](host: String,  bufferSize: Int, overflowStrategy: OverflowStrategy, converter: HttpResponse => Future[Seq[A]])extends Actor{
  import githubrank.GithubEntityJsonFormats._
  implicit val system = context.system
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  def connectionRef: Flow[(HttpRequest, ActorRef), (Try[HttpResponse], ActorRef), Http.HostConnectionPool] =
    Http().cachedHostConnectionPoolHttps[ActorRef](host = "api.github.com")

  private def source: Source[(HttpRequest, ActorRef), ActorRef] =
    Source.actorRef[(HttpRequest, ActorRef)](20, OverflowStrategy.dropNew)

  private def sink = Sink.foreach[(Try[HttpResponse], ActorRef)]{
    case (Success(resp), ref) =>
      self ! Response(Right(resp), ref)
    case (Failure(ex), ref) =>
      self ! Response(Left(ex), ref)
  }

  private val  requestPool = {
    source
      .log("logging request")
      .via(connectionRef)
      .to(sink)
      .run()
  }

  def convert(response: HttpResponse): Future[(Seq[A], immutable.Seq[HttpHeader])] = {
    val heads = response.headers
    response.status match {
      case StatusCodes.OK =>
        converter(response).map(e => e -> heads)
//        Unmarshal(response).to[Seq[GithubEntity]].map(e => e -> heads)
      case _ =>
        Unmarshal(response).to[String].foreach(println)
        Future(Seq.empty, heads)
    }
  }

  def nextUri(heads: Seq[HttpHeader]): Option[Uri] = {
    val linkHeader = heads.filter(_.isInstanceOf[Link]).map(_.asInstanceOf[Link]).flatMap(_.values)
    val params =  linkHeader.collectFirst{
      case linkV : LinkValue  if linkV.params.exists(param => param.key == "rel" && param.value() == "next") =>
        linkV.uri
    }
    params
  }

  def getNextRequest(headerss: Seq[HttpHeader]): Option[HttpRequest] = {
    val header = scala.collection.immutable.Seq(
      headers.RawHeader("Authorization", "token 4e29ca6bf86305a1119d0638a585894fa2c443d5"),
    )
    nextUri(headerss).map(next => HttpRequest(HttpMethods.GET, next, headers = header))
  }

  private val objectBuffer: scala.collection.mutable.ArrayBuffer[A] = scala.collection.mutable.ArrayBuffer()
  private var counter = 0
  override def receive: Receive = {
    case Request(url) =>
      val header = scala.collection.immutable.Seq(
        headers.RawHeader("Authorization", "token 4e29ca6bf86305a1119d0638a585894fa2c443d5"),
      )
      val replyTo = sender()
      requestPool ! HttpRequest(uri = url, headers = header) -> replyTo

    case Response(Right(response: HttpResponse), ref) =>
      convert(response) map{ resp =>
        resp._1 match {
          case value: Seq[A] =>
            counter = counter + value.length
            ref ! ResponseEntities(value.toList)
            getNextRequest(resp._2) match {
              case Some(value) =>
                self ! NextPage(value, ref)
              case None =>
                self ! Finish(ref)
            }
        }
      }
    case Response(Left(exception: Exception), ref) =>
      self ! Finish(ref)
    case NextPage(request, ref) =>
      requestPool ! request -> ref
    case Finish(ref)  =>
      ref ! Result[A](counter)
  }
}

trait RequestMessages
case class Request[A](url: String) extends RequestMessages
case class NextPage[A](request: HttpRequest, ref: ActorRef) extends RequestMessages
case class Finish(ref: ActorRef) extends RequestMessages
case class Response[A](response: Either[Throwable ,HttpResponse], senderRef: ActorRef)
case class Result[A](counts: Int)
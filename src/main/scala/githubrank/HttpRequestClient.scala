package githubrank

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Link, LinkValue}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
class  HttpRequestClient(entityMapper: HttpResponse => Future[Seq[GithubEntity]]) {
  import GithubEntityJsonFormats._
  import ErrorFormat._

  /*
  * Unmarshal HttpResponse to a tuple of (Seq[GithubEntity], Seq[Header]) given a function HttpResponse => Future[Seq[GithubEntity]] */
  def convert(response: HttpResponse)(implicit executionContext: ExecutionContext, materializer: Materializer): Future[(Seq[GithubEntity], immutable.Seq[HttpHeader])] = {
    val heads = response.headers
    response.status match {
      case StatusCodes.OK =>
        entityMapper(response).map(e => e -> heads)
      case _ =>
        Unmarshal(response).to[String].foreach(println)
        Future(Seq.empty, heads)
    }
  }

  /*
  * Extracts the Link header and decide if there is a next page to pull*/
  private def nextUri(heads: Seq[HttpHeader]): Option[Uri] = {
    val linkHeader = heads.filter(_.isInstanceOf[Link]).map(_.asInstanceOf[Link]).flatMap(_.values)
    val params =  linkHeader.collectFirst{
      case linkV : LinkValue  if linkV.params.exists(param => param.key == "rel" && param.value() == "next") =>
        linkV.uri
    }
    params
  }

  private def getNextRequest(headerss: Seq[HttpHeader]): Option[HttpRequest] = {
    val header = scala.collection.immutable.Seq(
      headers.RawHeader("Authorization", "token 2a0098a7b1ae76fc76bb5c507f54e15ee7fdd71a"),
    )
    nextUri(headerss).map(next => HttpRequest(HttpMethods.GET, next, headers = header))
  }

  /*
  * Recursively make requests to a paginated page, given that the pagination
  * details exists in the Link header returned from reach request
  * @param request: An Option of request to be made.*/
 private def paginationChain(request: Option[HttpRequest])(implicit mat: Materializer, system: ActorSystem)
                     :Future[Option[(Option[HttpRequest], Either[Error ,Seq[GithubEntity]])]] = {
   implicit val ec: ExecutionContext =  system.dispatcher
   request match {
     case Some(req) =>
       Http().singleRequest(req) flatMap { response =>
         response.status match {
           case StatusCodes.NotFound  =>
             Unmarshal(response).to[RepoNotFound].map { resp =>
               Some(None -> Left(resp))
             }
           case StatusCodes.Unauthorized =>
             Unmarshal(response).to[BadCredentials].map { resp =>
               Some(None -> Left(resp))
             }
           case StatusCodes.NoContent =>
             Unmarshal(response).to[String].foreach(e => system.log.error(s"error occurred while making request : $req. \n details: $e"))
             Future.successful(Some(None -> Right(Seq.empty)))
           case StatusCodes.OK =>
             convert(response) map { resp =>
               getNextRequest(resp._2) match {
                 case Some(request) =>
                   Some(Some(request) -> Right(resp._1))
                 case None =>
                   Some(None -> Right(resp._1))
               }
             }
         }
       }
     case None =>
       Future.successful(None)
   }
 }

  /*
  * Returns a Source that emits Option[Seq[GithubEntity]] and can be materialized somewhere..
  * @param: request: he request to be made */
  def makeRequest(request: Option[HttpRequest])(implicit materializer: Materializer, system: ActorSystem): Source[Either[Error, Seq[GithubEntity]], NotUsed] = {
    Source.unfoldAsync(request)(paginationChain)
  }

  /*
  * This is a materailized version of makeRequest() in case we dont want to handle the source materialization.*/
  def makeRequestWithResponse(request: Option[HttpRequest])(implicit materializer: Materializer, system: ActorSystem): Future[immutable.Seq[Either[Error, Seq[GithubEntity]]]] = {
    implicit val ec = system.dispatcher
       makeRequest(request)
         .runWith(Sink.seq[Either[Error, Seq[GithubEntity]]])
  }
}

package githubrank

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Link, LinkValue}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.slf4j.Logger

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

class HttpRequestClient(entityMapper: HttpResponse => Future[Seq[GithubEntity]], apiKey: String, log: Logger) {

  import ErrorFormat._
  import GithubEntityJsonFormats._

  val defaultHeaders = List(headers.RawHeader("Authorization", s"token $apiKey"))

  /*
  * This is a materailized version of makeRequest() in case we dont want to handle the source materialization.*/
  def makeRequestWithResponse(request: Option[Uri])(implicit materializer: Materializer, system: ActorSystem[RankMessages]): Future[immutable.Seq[Either[Error, Seq[GithubEntity]]]] = {
    implicit val ec = system.executionContext
    makeRequest(request)
      .runWith(Sink.seq[Either[Error, Seq[GithubEntity]]])
  }

  /*
  * Returns a Source that emits Option[Seq[GithubEntity]] and can be materialized somewhere..
  * @param: request: he request to be made */
  def makeRequest(uri: Option[Uri])(implicit materializer: Materializer, system: ActorSystem[RankMessages]): Source[Either[Error, Seq[GithubEntity]], NotUsed] = {
    Source.unfoldAsync(uri)(paginationChain)
  }

  /*
  * Recursively make requests to a paginated page, given that the pagination
  * details exists in the Link header returned from reach request
  * @param request: An Option of request to be made.*/
  def paginationChain(uri: Option[Uri])(implicit mat: Materializer, system: ActorSystem[RankMessages])
  : Future[Option[(Option[Uri], Either[Error, Seq[GithubEntity]])]] = {
    implicit val ec: ExecutionContext = system.executionContext
    uri match {
      case Some(uri) =>
        val request = HttpRequest(method = HttpMethods.GET, uri = uri, headers = defaultHeaders)
        Http().singleRequest(request) flatMap { response =>
          response.status match {
            case StatusCodes.NotFound =>
              Unmarshal(response).to[RepoNotFound].map { resp =>
                log.error(s"repository not found: ${resp}")
                Some(None -> Left(resp))
              }
            case StatusCodes.Unauthorized =>
              Unmarshal(response).to[BadCredentials].map { resp =>
                log.error(s"Unauthorized request, please check provided credentials: ${resp}")
                Some(None -> Left(resp))
              }
            case StatusCodes.NoContent =>
              Unmarshal(response).to[String].foreach(e => log.error(s"error occurred while making request : $request. \n details: $e"))
              Future.successful(Some(None -> Right(Seq.empty)))
            case StatusCodes.OK =>
             convert(response) map { resp =>
               log.debug(s"response for request: ${request.uri}: data gotten ${resp._1.length}")
               getNextRequest(resp._2) match {
                 case Some(request) =>
                   log.debug(s"Not done yet.. Fetching next page.. : ${request.toString()}")
                   Some(Some(request) -> Right(resp._1))
                 case None =>
                   log.debug(s"no next request found: end of chain: last page data:  ${resp._1.size}")
                   Some(None -> Right(resp._1))
               }
             }
            case _ =>
              Unmarshal(response).to[String].foreach(e => log.error(s"uncaught response retuned : $request. \n details: $e"))
              Future.successful(Some(None -> Right(Seq.empty)))
          }
        }
      case None =>
        Future.successful(None)
    }
  }

  /*
  * Unmarshal HttpResponse to a tuple of (Seq[GithubEntity], Seq[Header]) given a function HttpResponse => Future[Seq[GithubEntity]] */
  def convert(response: HttpResponse)(implicit executionContext: ExecutionContext, materializer: Materializer): Future[(Seq[GithubEntity], immutable.Seq[HttpHeader])] = {
    val heads = response.headers
    response.status match {
      case StatusCodes.OK =>
        entityMapper(response).map(e => e -> heads)
      case _ =>
        Unmarshal(response).to[String].foreach(log.error)
        Future(Seq.empty, heads)
    }
  }

  private def getNextRequest(headerss: Seq[HttpHeader]): Option[Uri] = {
    nextUri(headerss)
  }

  /*
  * Extracts the Link header and decide if there is a next page to pull*/
  private def nextUri(heads: Seq[HttpHeader]): Option[Uri] = {
    val linkHeader = heads.collect { case l: Link => l }.flatMap(_.values)
    val params = linkHeader.collectFirst {
      case linkV: LinkValue if linkV.params.exists(param => param.key == "rel" && param.value() == "next") =>
        linkV.uri
    }
    params
  }
}

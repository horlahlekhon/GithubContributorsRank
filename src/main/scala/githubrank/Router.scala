package githubrank

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.CachingDirectives.{cache, routeCache}
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.pattern.ask
import akka.util.Timeout
import spray.json._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._

class Router(implicit val system: ActorSystem, timeout: Timeout) {
import GithubEntityJsonFormats._
import system.dispatcher

  val route: Route =
    (path("org" / Segment / "contributors") & get) { org =>
      cache(caching, cacheKey) {
        withRequestTimeout(40 seconds) {
          complete {
            getContributions(org) map {
              case Left(error) =>
                error match {
                  case BadCredentials(error) =>
                    HttpResponse(entity =
                      HttpEntity(
                        ContentTypes.`application/json`,
                        s"""{"error": "${error}"}"""
                      ), status = StatusCodes.Unauthorized
                    )
                  case RepoNotFound(error) =>
                    HttpResponse(entity =
                      HttpEntity(
                        ContentTypes.`application/json`,
                        s"""{"error": "${error}"}"""
                      ), status = StatusCodes.NotFound
                    )
                }
              case Right(contribs) =>
                system.log.info(s"Contributors length: ${contribs.length}")
                HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, contribs.toJson.prettyPrint))
            }
          }
        }
      }
    }
  private val registryActor = system.actorOf(Props[RankActor], "GithubRankActor")
  private val cacheKey: PartialFunction[RequestContext, Uri] = {
    case context: RequestContext if context.request.method == GET => context.request.uri
  }

  private val caching = routeCache[Uri]

  private def getContributions(org: String): Future[Either[Error, Seq[Contributor]]] =
    (registryActor ? GetContributions(org)).mapTo[Either[Error, Seq[Contributor]]]
}

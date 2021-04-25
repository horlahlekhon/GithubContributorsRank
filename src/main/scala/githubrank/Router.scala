package githubrank

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.CachingDirectives.{cache, routeCache}
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

class Router(implicit timeout: Long, askTimeoutException: Timeout, system: ActorSystem[RankMessagesTyped]) {

  import GithubEntityJsonFormats._

  implicit val sys = system.classicSystem

  import system.executionContext

  implicit val scheduler: Scheduler = system.scheduler
  val route: Route =
    (path("org" / Segment / "contributors") & get) { org =>
      cache(caching, cacheKey) {
        withRequestTimeout(timeout.seconds) {
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

  private val cacheKey: PartialFunction[RequestContext, Uri] = {
    case context: RequestContext if context.request.method == GET => context.request.uri
  }

  private val caching = routeCache[Uri]

  private def getContributions(org: String): Future[Either[Error, Seq[Contributor]]] =
    system.ask(ref => GetContributionsTyped(org, ref)).mapTo[Either[Error, Seq[Contributor]]]
}

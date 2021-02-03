package githubrank

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

object Main extends App {

  implicit val system = ActorSystem("github-rank")
  implicit val materializer =  ActorMaterializer()
  implicit val ctx: ExecutionContext = system.dispatcher
  implicit val timeout = Timeout.create(system.settings.config.getDuration("github-ranks.routes.ask-timeout"))


  val routes = new Router

  system.log.info("Akka http server started at localhost:8080")
  val (host, port) = ("localhost", 8080)
  val bind: Future[ServerBinding] = Http().newServerAt(host, port).bindFlow(routes.route)

  bind.failed.foreach(exception => system.log.error(exception, s" Failed to create server at $host:$port"))

}

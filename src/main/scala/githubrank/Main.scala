package githubrank

//import akka.actor.ActorSystem

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

object Main extends App {

  val config = ConfigFactory.parseFile(new File("src/main/resources/application.conf")).resolve()
  val actor = RankActor(config.getString("apiKey"))
  implicit val system: ActorSystem[RankMessagesTyped] = ActorSystem(actor, "Github-Rank-Actor")
  implicit val ex = system.executionContext
  implicit val askTimout: Timeout = Timeout(config.getDuration("github-ranks.routes.ask-timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)
  implicit val requestTimeout = config.getDuration("request_timout", TimeUnit.SECONDS)
  val routes = new Router

  system.log.info("Akka http server started at localhost:8080")
  val (host, port) = ("localhost", 8080)
  val bind: Future[ServerBinding] = Http().newServerAt(host, port).bindFlow(routes.route)

  bind.failed.foreach(exception => system.log.error(s" Failed to create server at $host:$port", exception))

}

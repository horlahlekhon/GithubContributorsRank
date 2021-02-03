package githubrank

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, headers}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object ANotherMain  extends App {

  implicit val system = ActorSystem("github-rank")
  implicit val materializer =  ActorMaterializer()
  implicit val ctx: ExecutionContext = system.dispatcher
  val header = scala.collection.immutable.Seq(
    headers.RawHeader("Authorization", "token 4e29ca6bf86305a1119d0638a585894fa2c443d5"),
  )
import GithubEntityJsonFormats._
  implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("github-ranks.routes.ask-timeout"))
  def converter(httpResponse: HttpResponse)(implicit mat: Materializer): Future[Seq[GithubEntity]] = {
    Try(Unmarshal(httpResponse).to[Seq[GithubEntity]])
      .fold(fa => Future.successful(Seq.empty), fb => fb)
    }

  val http  = new  HttpRequestClient(converter)

  val request = HttpRequest(uri = "https://api.github.com/repos/aransiolaii/Sunset-theme/contributors", headers = header )

  val resp = http.makeRequest(Some(request))



  val ace = 284
  val `Sunset-theme` = 3
  val `forty-jekyll-webpage` =  1
}

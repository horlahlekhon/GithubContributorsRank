package githubrank

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import githubrank.ANotherMain.header
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class HttpRequestClientSpec extends TestKit(ActorSystem("GithubRankActorTest")) with AsyncWordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar{
import GithubEntityJsonFormats._
//  override def afterAll(): Unit = {
//    TestKit.shutdownActorSystem(system)
//  }
  implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("github-ranks.routes.ask-timeout"))
  def converter(httpResponse: HttpResponse)(implicit mat: Materializer): Future[Seq[GithubEntity]] = {
    Try(Unmarshal(httpResponse).to[Seq[GithubEntity]])
      .fold(fa => Future.successful(Seq.empty), fb => fb)
  }
//case class TestContributors(login: String, contributions)
val http = mock[HttpRequestClient]
//  "HttpRequestClient" should {
//    "Recursively get paginated data with precision" in  {
//      val requestSource =  http.makeRequestWithResponse(Some(HttpRequest(uri = "https://api.github.com/repos/aransiolaii/ace/contributors", headers = header )))
//
//      requestSource.map { e =>
//       e.flatten.flatten.size shouldEqual 23
//      }
//
//    }
//  }

}

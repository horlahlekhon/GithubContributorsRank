package githubrank

import java.io.File

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

import scala.concurrent.Future
import scala.util.Try

class HttpRequestClientSpec extends AsyncWordSpecLike with Matchers with BeforeAndAfterAll {
  val testConf: Config = ConfigFactory.parseFile(new File("src/test/resources/application.conf")).resolve()
  val apiKey: String = testConf.getString("apiKey")

  import GithubEntityJsonFormats._

  val behaviour = RankActor(apiKey)
  implicit val sys: ActorSystem[RankMessagesTyped] = ActorSystem(Behaviors.empty, "Github-Rank-Actor")
  implicit val ex = sys.executionContext
  implicit val timeout: Timeout = Timeout.create(testConf.getDuration("github-ranks.routes.ask-timeout"))
  val classicSys = sys.classicSystem

  override def afterAll(): Unit = {
    sys.terminate()
  }

  def converter(httpResponse: HttpResponse)(implicit mat: Materializer): Future[Seq[GithubEntity]] = {
    Try(Unmarshal(httpResponse).to[Seq[GithubEntity]])
      .fold(fa => Future.successful(Seq.empty), fb => fb)
  }

  "httpRequestClient" when {
    ".paginationChain" should {
      " Send a request and check that there is no next page given the link headers" in {
        val request = new HttpRequestClient(converter, apiKey, sys.log)
        val req = RankActor.orgUri("aransiolaii", 20)
        val res = request.paginationChain(Some(req))
        res map {
          case Some(value) =>
            value._1 match {
              case Some(value) =>
                fail("Test case failed: there should not be any more pages")
              case None =>
                succeed
            }
          case _ =>
            fail("Test case failed: there should not be any more pages")
        }
      }
      "Send a request and check that there is a next page given the link headers and return a http request to go to the page" in {
        val request = new HttpRequestClient(converter, apiKey, sys.log)
        val req = RankActor.reposUri("aransiolaii", "ace", 10)
        val res = request.paginationChain(Some(req))
        res map {
          case Some(value) =>
            value._1 match {
              case Some(value) =>
                succeed
              case None =>
                fail("Test case failed: there should more pages")
            }
          case None => fail("A request was passed, we shouldnt get None")
        }
      }
    }
    ".makeRequest" should {
      "recursively get paginated pages given pagination information in each response headers" in {
        val httpRequestClient = new HttpRequestClient(converter, apiKey, sys.log)
        val req = RankActor.orgUri("SkylarkDoyle", 1)
        val src = httpRequestClient.makeRequest(Some(req))
        val resp = src.runWith(Sink.seq)
        resp.map { e =>
          e.length shouldEqual 3
        }
      }
    }
  }

}

package githubrank

import java.io.File

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

class RouteSpec extends AsyncWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  import GithubEntityJsonFormats._

  implicit def default = RouteTestTimeout(20.seconds)

  implicit val timeout = Timeout.create(system.settings.config.getDuration("github-ranks.routes.ask-timeout"))
  implicit val requestTimeout = 50L
  val apiKey = ConfigFactory.parseFile(new File("src/it/resources/application.conf")).resolve().getString("apiKey")
  val behaviour = RankActor(apiKey)
  implicit val sys: ActorSystem[RankMessagesTyped] = ActorSystem(behaviour, "Github-Rank-Actor")
  implicit val ex = sys.executionContext
  val router = new Router
  val org = "aransiolaii"

  val syss = sys.classicSystem

  override def afterAll(): Unit = {
    super.afterAll()
    //    TestKit.shutdownActorSystem(system)
  }

  "GET org/<org>/contributors" should {
    "Return List of contributors and their total contributions across all project given an organisation" in {
      Get(s"/org/${org}/contributors") ~> router.route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Seq[Contributor]].size shouldEqual 288
      }
    }

    "Return 404 notfound if the organisation doesnt exist on github" in {
      Get(s"/org/araolaii/contributors") ~!> router.route ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
}

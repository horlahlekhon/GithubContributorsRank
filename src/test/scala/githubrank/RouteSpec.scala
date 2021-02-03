package githubrank

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{AsyncWordSpec, AsyncWordSpecLike, BeforeAndAfterAll, Matchers}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.util.Timeout
import githubrank.ErrorFormat.repoNotFoundJsonFormat

import scala.concurrent.duration._
class RouteSpec extends AsyncWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  import GithubEntityJsonFormats._
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(20.seconds)
  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }
  implicit val timeout = Timeout.create(system.settings.config.getDuration("github-ranks.routes.ask-timeout"))

  val router = new Router
  val org = "aransiolaii"
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

package githubrank

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

import scala.util

class GithubRankSpec extends TestKit(ActorSystem("GithubRankActorTest")) with AsyncWordSpecLike with Matchers with BeforeAndAfterAll{
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
  implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("github-ranks.routes.ask-timeout"))


  "RankActor" must {
    "Send back a valid and sorted list of contributors given a valid org in message 'GetContributions'" in {
      val rankActor = system.actorOf(Props[RankActor], "GithubRankActor-1")
      val contribsFuture = (rankActor ? GetContributions("aransiolaii")).mapTo[Either[Error, Seq[Contributor]]]

      contribsFuture.map {
        case Right(value) =>
          value.size shouldBe > (1)
        case Left(value) =>
          fail("none returned instead of a Seq[Contributor]")
      }
    }
    "Send back an Error given an invalid org in message 'GetContributions'" in {
      val rankActor = system.actorOf(Props[RankActor], "GithubRankActor-2")
      val contribsFuture = (rankActor ? GetContributions("aransdszaii")).mapTo[Either[Error, Seq[Contributor]]]
      contribsFuture.map { e =>
          e.isLeft shouldEqual true
      }
    }
  }
}

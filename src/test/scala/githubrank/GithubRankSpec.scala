package githubrank

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

import scala.concurrent.duration.Duration

class GithubRankSpec extends AsyncWordSpecLike with Matchers with BeforeAndAfterAll {

  val testKit = ActorTestKit()
  val testConf = ConfigFactory.parseFile(new File("src/test/resources/application.conf")).resolve()

  implicit val time: Timeout = Timeout(Duration(50L, TimeUnit.SECONDS))
  val apiKey = testConf.getString("apiKey")

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "RankActor" should {
    "Send back a valid and sorted list of contributors given a valid org in message 'GetContributions'" in {
      val probe = testKit.createTestProbe[Either[Error, Seq[GithubEntity]]]()
      val rankActor = testKit.spawn(RankActor(apiKey))
      val contribsFuture = rankActor ! GetContributionsTyped("aransiolaii", probe.ref) //[Either[Error, Seq[Contributor]]]
      val message = probe.expectMessageType[Either[Error, Seq[Contributor]]](Duration(50L, TimeUnit.SECONDS))
      message match {
        case Right(value) =>
          value.size shouldBe >(1)
        case Left(value) =>
          fail("error returned instead of a Seq[Contributor]")
      }
    }
    "Send back an Error given an invalid org in message 'GetContributions'" in {
      val probe = testKit.createTestProbe[Either[Error, Seq[GithubEntity]]]()
      val rankActor = testKit.spawn(RankActor(apiKey))
      val contribsFuture = rankActor ! GetContributionsTyped("aransolaii", probe.ref) //[Either[Error, Seq[Contributor]]]
      val message = probe.expectMessageType[Either[Error, Seq[Contributor]]](Duration(50L, TimeUnit.SECONDS))
      message.isLeft shouldEqual true
    }
  }
}

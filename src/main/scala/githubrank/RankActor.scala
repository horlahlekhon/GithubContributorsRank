package githubrank

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, Uri, headers}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.Future
import scala.util.Try


sealed trait RankMessages {
  def replyTo: ActorRef[Either[Error, Seq[Contributor]]]
}

case class GetContributions(org: String, replyTo: ActorRef[Either[Error, Seq[GithubEntity]]]) extends RankMessages

object RankActor {

  import GithubEntityJsonFormats._

  val orgUri: (String, Int) => Uri = (org: String, perPage: Int) => Uri(s"https://api.github.com/users/${org}/repos?per_page=$perPage")
  val reposUri: (String, String, Int) => Uri = (org: String, repo: String, perPage: Int) => Uri(s"https://api.github.com/repos/${org}/${repo}/contributors?per_page=$perPage")

  def apply(apiKey: String): Behavior[RankMessages] = Behaviors.receive { (context, message) =>

    implicit val system = context.system.asInstanceOf[ActorSystem[RankMessages]]
    import system.executionContext
    val requestClient = new HttpRequestClient(converter, apiKey, system.log)
    val perPage = 100
    message match {
      case GetContributions(org, replyTo) =>
        requestClient.makeRequest(Some(orgUri(org, perPage)))
          .mapAsyncUnordered(3) {
            case Right(repos) =>
              system.log.info(s"organisation found: ${org}.. repos found: ${repos.length}")
              val fut = fetchRepoContributors(repos.toList, org, requestClient)
              fut.map(Right(_))
            case left@Left(_) =>
              system.log.error(s"Couldn't fetch the organisation. reason: ${left}")
              Future(left)
          }.map(e => e)
          .runForeach(e => replyTo ! e )
        Behaviors.same
    }
  }

  def converter(httpResponse: HttpResponse)(implicit mat: Materializer): Future[Seq[GithubEntity]] = {
    Try(Unmarshal(httpResponse).to[Seq[GithubEntity]])
      .fold(fa => Future.successful(Seq.empty), fb => fb)
  }

  def fetchRepoContributors(repos: List[GithubEntity], org: String, requestClient: HttpRequestClient)(implicit system: ActorSystem[RankMessages]): Future[Seq[Contributor]] = {
    import system.executionContext
    system.log.info(s"fetching contributors for repos: $repos")
    val r = Source(repos)
      .mapAsyncUnordered(3) { repo =>
        val request = reposUri(org, repo.asInstanceOf[Repo].name, 100)
        requestClient.makeRequest(Some(request))
          .runWith(Sink.seq[Either[Error, Seq[GithubEntity]]])
      }.runReduce[Seq[Either[Error, Seq[GithubEntity]]]](_ ++ _).map { e =>
      val contribs = e.collect { case Right(value) => value }.flatten
      system.log.info(s"Contributors size with duplicates: ${contribs.size}")

      val res = contribs.map(_.asInstanceOf[Contributor])
        .groupBy(_.login).map {
        case (str, contributors) => Contributor(str, contributors.map(_.contributions).sum)
      }.toList
        .sortBy(_.contributions)(Ordering[Int].reverse)
      system.log.info(s"fetched contributors after merging duplicates: ${res.length}")
      res
    }
    r
  }
}

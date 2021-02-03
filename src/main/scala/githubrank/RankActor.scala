package githubrank

import akka.actor.Actor
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, headers}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait RankMessages

case class GetContributions(org: String) extends RankMessages

case class ResponseEntities[A](responses: List[A])

case class GetContributors(repoName: String)


class RankActor extends Actor {
  implicit val system = context.system
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  import GithubEntityJsonFormats._

  val header = scala.collection.immutable.Seq(
    headers.RawHeader("Authorization", "token 2a0098a7b1ae76fc76bb5c507f54e15ee7fdd71a"),
  )
  val requestClient = new HttpRequestClient(converter)

  def converter(httpResponse: HttpResponse)(implicit mat: Materializer): Future[Seq[GithubEntity]] = {
    Try(Unmarshal(httpResponse).to[Seq[GithubEntity]])
      .fold(fa => Future.successful(Seq.empty), fb => fb)
  }

  def fetchRepoContributorss(repos: List[GithubEntity], org: String): Future[List[Contributor]] = {
    val requests = repos.map {
      case Repo(name) =>
        val req = HttpRequest(uri = s"https://api.github.com/repos/${org}/$name/contributors?per_page=100", headers = header)
        requestClient.makeRequestWithResponse(Some(req))
    }
    Future
      .sequence(requests)
      .map(e =>
        e.flatten.map(_.right.get)
          .map(_.asInstanceOf[Contributor]))
      .map(contributors => contributors.sortBy(_.contributions)(Ordering[Int].reverse))
  }

  override def receive: Receive = {
    case GetContributions(org) =>
      val replyTo = sender()
      val requ = HttpRequest(uri = s"https://api.github.com/users/${org}/repos?per_page=100", headers = header)
     requestClient.makeRequest(Some(requ))
        .mapAsyncUnordered(3) {
          case Left(error) =>
            Future(Left(error))
          case Right(repos) =>
           val fut =  fetchRepoContributors(repos.toList, org)
              fut.map(Right(_))
        }.runWith(Sink.foreach[Either[Error, Seq[Contributor]]] { value =>
       replyTo ! value
     })
  }

  /*
  * Fetch contributors for each repository and make sure to drill down each repo that might have more than one page..
  * In some cases where repository contributor size is large, like some google repository, github doesnt return complete data
  * and return with message
  * {"message":"The history or contributor list is too large to list contributors for
  *  this repository via the API.","documentation_url":"https://docs.github.com/rest/reference/repos#list-repository-contributors"
  * @param repos: List of repositories to get contributors for
  * */
  def fetchRepoContributors(repos: List[GithubEntity], org: String): Future[Seq[Contributor]] = {
    Source(repos)
      .mapAsyncUnordered(3) { repo =>
        val request = HttpRequest(uri = s"https://api.github.com/repos/${org}/${repo.asInstanceOf[Repo].name}/contributors?per_page=100", headers = header)
        requestClient.makeRequest(Some(request)).map(_.right.get)
          .runWith(Sink.reduce[Seq[GithubEntity]](_ ++ _))
      }.runWith(Sink.reduce[Seq[GithubEntity]](_ ++ _)).map { e =>
      system.log.info(s"Contributors size with duplicates: ${e.length}")
      val res = e.
        map(_.asInstanceOf[Contributor])
        .groupBy(_.login).map {
        case (str, contributors) => Contributor(str, contributors.map(_.contributions).sum)
      }.toList
        .sortBy(_.contributions)(Ordering[Int].reverse)
      system.log.info(s"fetched contributors after merging duplicates: ${res.length}")
      res
    }
  }
}



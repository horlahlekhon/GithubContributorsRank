//package githubrank
//import akka.actor.ActorSystem
//import akka.http.scaladsl.model.HttpRequest
//import akka.stream.Materializer
//import org.scalamock.clazz.MockImpl.{mock, toMockFunction1}
//import org.scalamock.scalatest._
//
//import scala.collection.immutable
//import scala.concurrent.Future
//trait MockHttpClient extends HttpRequestClient {
//val mocker = toMockFunction1[Option[HttpRequest], Future[immutable.Seq[Option[Seq[GithubEntity]]]]]
//
//  override def makeRequestWithResponse(request: Option[HttpRequest])(implicit materializer: Materializer, system: ActorSystem): Future[immutable.Seq[Option[Seq[GithubEntity]]]] = {
//    mocker(request)
//  }
//
//}

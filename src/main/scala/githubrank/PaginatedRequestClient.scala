package githubrank

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}

class PaginatedRequestClient[A](converter: HttpResponse => Future[Seq[A]])(implicit materializer: Materializer, system: ActorSystem, executionContext: ExecutionContext) {

}

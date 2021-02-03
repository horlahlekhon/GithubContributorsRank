package githubrank

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import scala.util.Try
trait GithubEntity

case class Contributor(login: String, contributions: Int) extends GithubEntity
case class Repo(name: String) extends GithubEntity
object GithubEntityJsonFormats extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val contributorJsonFormat: RootJsonFormat[Contributor] = jsonFormat2(Contributor.apply)
  implicit val repoJsonFormat: RootJsonFormat[Repo] = jsonFormat(Repo, "name")
  implicit object GithubEntityFormat extends RootJsonFormat[GithubEntity] {
    override def write(obj: GithubEntity): JsValue = obj match {
      case contrib: Contributor =>
        contrib.toJson
      case repo: Repo =>
        repo.toJson
    }

    override def read(json: JsValue): GithubEntity = {
      json.asJsObject.fields.get("login") match {
        case Some(value) =>
          json.convertTo[Contributor]
        case None =>
          json.convertTo[Repo]
      }
    }
  }
}

object GithubEntity {
  def apply(githubEntity: GithubEntity): Either[Repo, Contributor] = {
    Try(githubEntity.asInstanceOf[Repo]).fold(fa => Right(githubEntity.asInstanceOf[Contributor]),  fb => Left(fb))
  }
}


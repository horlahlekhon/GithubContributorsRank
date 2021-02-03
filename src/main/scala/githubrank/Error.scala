package githubrank

import githubrank.GithubEntityJsonFormats.jsonFormat2
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait Error {
  def message: String
}
case class RepoNotFound(message:  String) extends Error
case class BadCredentials(message: String) extends Error

object ErrorFormat extends DefaultJsonProtocol{
  implicit val repoNotFoundJsonFormat: RootJsonFormat[RepoNotFound] = jsonFormat1(RepoNotFound.apply)
  implicit val badCredentialsJsonFormat: RootJsonFormat[BadCredentials] = jsonFormat1(BadCredentials.apply)


}
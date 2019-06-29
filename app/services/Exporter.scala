package services
import javax.inject._
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, JsonValidationError, Reads, _}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Exporter @Inject()(wsClient: WSClient, configuration: Configuration)(implicit ec: ExecutionContext) {

  private val authKey = configuration.get[String]("github.authKey")
  private val logger  = Logger(this.getClass)

  def getAll(ownerWithRepo: OwnerWithRepo): Future[List[PullRequestWithReviewers]] =
    for {
      allPrs              <- getAllPrs(ownerWithRepo)
      allPrsWithReviewers <- getPrsWithReviewers(allPrs, ownerWithRepo)
    } yield allPrsWithReviewers

  private def getPrsWithReviewers(prs: List[PullRequest],
                                  ownerWithRepo: OwnerWithRepo): Future[List[PullRequestWithReviewers]] =
    Future.sequence(prs.map(pr => getReviewers(ownerWithRepo, pr.number).map(PullRequestWithReviewers(pr, _))))

  private def getAllPrs(ownerWithRepo: OwnerWithRepo) =
    wsClient
      .url(s"${baseUrl(ownerWithRepo)}/pulls?state=closed&per_page=100&sort=created&direction=desc")
      .withHttpHeaders("Authorization" -> authKey)
      .get()
      .map { response =>
        Json
          .parse(response.body)
          .validate[List[PullRequest]]
          .fold(errors => error(errors), r => r)
      }

  private def getReviewers(ownerWithRepo: OwnerWithRepo, pullNumber: Long) =
    wsClient
      .url(s"${baseUrl(ownerWithRepo)}/pulls/$pullNumber/reviews")
      .withHttpHeaders("Authorization" -> authKey)
      .get()
      .map { response =>
        Json
          .parse(response.body)
          .validate[List[PullRequestReviewer]]
          .fold(errors => error(errors), r => r)
      }

  private def error(errors: Seq[(JsPath, collection.Seq[JsonValidationError])]) = {
    logger.error(errors.toString)
    throw new RuntimeException(errors.toString)
  }

  private def baseUrl(ownerWithRepo: OwnerWithRepo) =
    s"https://api.github.com/repos/${ownerWithRepo.owner}/${ownerWithRepo.repo}"

}

case class OwnerWithRepo(owner: String, repo: String)

case class PullRequestWithReviewers(pr: PullRequest, reviewres: List[PullRequestReviewer])

case class Repository(name: String)

object Repository {
  implicit val reads: Reads[Repository] = Json.reads[Repository]
}

case class PullRequest(number: Long,
                       title: String,
                       user: User,
                       htmlUrl: String,
                       createdAt: String,
                       requested_reviewers: List[User],
                       merged_at: Option[String],
                       repoName: String)

object PullRequest {
  implicit val reads: Reads[PullRequest] = (
    (__ \ "number").read[Long] and
      (__ \ "title").read[String] and
      (__ \ "user").read[User] and
      (__ \ "html_url").read[String] and
      (__ \ "created_at").read[String] and
      (__ \ "requested_reviewers").read[List[User]] and
      (__ \ "merged_at").readNullable[String] and
      (__ \ "head" \ "repo" \ "name").read[String]
  )(PullRequest.apply _)

}

case class PullRequestReviewer(user: User, state: String)

object PullRequestReviewer {
  implicit val reads: Reads[PullRequestReviewer] = Json.reads[PullRequestReviewer]
}

case class User(login: String)

object User {
  implicit val reads: Reads[User] = Json.reads[User]
}

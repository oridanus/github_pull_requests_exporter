package services

import javax.inject._
import play.api.Configuration
import play.api.libs.json.{Json, Reads}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Exporter @Inject()(wsClient: WSClient, configuration: Configuration)(implicit ec: ExecutionContext) {

  private val authKey = configuration.get[String]("github.authKey")

  def getAll(repo: String): Future[List[PullRequestWithReviewers]] = for {
    allPrs <- getAllPrs(repo)
    allPrsWithReviewers <- getPrsWithReviewers(allPrs, repo)
  } yield allPrsWithReviewers

  private def getPrsWithReviewers(prs: List[PullRequest], repo: String): Future[List[PullRequestWithReviewers]] =
    Future.sequence(prs.map(pr => {
      getReviewers(repo, pr.number).map(PullRequestWithReviewers(pr, _))
    }))

  private def getAllPrs(repo: String) = wsClient
    .url(s"https://api.github.com/repos/hibobio/$repo/pulls?state=closed&per_page=100&sort=created&direction=desc")
    .withHttpHeaders("Authorization" -> authKey)
    .get()
    .map {
      response => Json.parse(response.body).validate[List[PullRequest]].get
    }

  private def getReviewers(repo: String, pullNumber: Long) = wsClient
    .url(s"https://api.github.com/repos/hibobio/$repo/pulls/$pullNumber/reviews")
    .withHttpHeaders("Authorization" -> authKey)
    .get()
    .map {
      response => Json.parse(response.body).validate[List[PullRequestReviewer]].get
    }

}

case class PullRequestWithReviewers(pr: PullRequest, reviewres: List[PullRequestReviewer])

case class PullRequest(number: Long, title: String, user: User, html_url: String, created_at: String, requested_reviewers: List[User], merged_at: Option[String])

object PullRequest {
  implicit val reads: Reads[PullRequest] = Json.reads[PullRequest]
}

case class PullRequestReviewer(user: User, state: String)

object PullRequestReviewer {
  implicit val reads: Reads[PullRequestReviewer] = Json.reads[PullRequestReviewer]
}

case class User(login: String)

object User {
  implicit val reads: Reads[User] = Json.reads[User]
}
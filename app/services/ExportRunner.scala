package services

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.github.tototoshi.csv.CSVWriter
import javax.inject._
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

@Singleton
class ExportRunner @Inject()(exporter: Exporter, csvExporter: CSVExporter, configuration: Configuration)(
    implicit ec: ExecutionContext) {

  private val logger =
    Logger(this.getClass)

  private val owner           = configuration.get[String]("github.owner")
  private val reposList       = configuration.get[String]("github.repos")
  private val ownersWithRepos = reposList.split(",").map(OwnerWithRepo(owner, _)).toList
  private val exportFileName  = Paths.get(configuration.get[String]("target.export.file.path"))
  private val statsFileName   = Paths.get(configuration.get[String]("stats.file.path"))

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  private def export() =
    Future
      .foldLeft[List[PullRequestWithReviewers], List[PullRequestWithReviewers]](
        ownersWithRepos.map(exporter.getAll)
      )(Nil)(_ ++ _)
      .map(_.filter(_.pr.merged_at.nonEmpty))
      .map(_.filter(createdLast2Weeks))
      .map(createCSV)
      .map(createStatistics)
      .map(_ => true)

  private def createdLast2Weeks(pullRequestWithReviewers: PullRequestWithReviewers) =
    LocalDateTime
      .parse(pullRequestWithReviewers.pr.createdAt, dateTimeFormatter)
      .isAfter(LocalDateTime.now().minusDays(14))

  private def createStatistics(prs: List[PullRequestWithReviewers]) = {

    val mostNonApprovedByUser: List[NonApproveByUser] =
      prs
        .filterNot(_.approved)
        .groupBy(_.pr.user)
        .map { case (user, nonApprovedPrs) => NonApproveByUser(user, nonApprovedPrs.size) }
        .toList
        .sortBy(_.numberOfNonApprovedPrs)
        .reverse
    csvExporter.writeCsvFile(
      statsFileName,
      List("User", "Number Of Non Approved PRs"),
      mostNonApprovedByUser.map(
        nonApproveByUser => List(nonApproveByUser.user.login, nonApproveByUser.numberOfNonApprovedPrs.toString)
      )
    )
    prs
  }

  private def createCSV(prs: List[PullRequestWithReviewers]) = {
    csvExporter.writeCsvFile(
      exportFileName,
      List("Repo", "Title", "User", "Url", "Creation Date", "Merged Date", "Requested reviewer", "Approved"),
      prs.map(pr => {
        val firstReviewer = pr.pr.requested_reviewers.headOption
          .map(_.login)
          .orElse(pr.reviewres.headOption.map(_.user.login))
          .getOrElse("")
        List(pr.pr.repoName,
             pr.pr.title,
             pr.pr.user.login,
             pr.pr.htmlUrl,
             pr.pr.createdAt,
             pr.pr.merged_at.getOrElse(""),
             firstReviewer,
             pr.approved.toString)
      })
    )
    prs
  }

  // This code is called when the application starts.
  export().map(_ => {
    logger.info("Finished exporting")
    println("Finished exporting")
  })

}

case class NonApprovedStats(numberOfNonApprovedPrs: Int, totalNumberOfPrs: Int)

case class NonApproveByUser(user: User, numberOfNonApprovedPrs: Int)

@Singleton
class CSVExporter() {

  def writeCsvFile(filePath: Path, header: List[String], rows: List[List[String]]): Try[Unit] =
    Try(new CSVWriter(new BufferedWriter(new FileWriter(filePath.toFile)))).flatMap((csvWriter: CSVWriter) =>
      Try {
        csvWriter.writeAll(
          header +: rows
        )
        csvWriter.close()
      } match {
        case f @ Failure(_) =>
          Try(csvWriter.close()).recoverWith {
            case _ => f
          }
        case success =>
          success
    })

}

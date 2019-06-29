package services

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.{Path, Paths}

import com.github.tototoshi.csv.CSVWriter
import javax.inject._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

@Singleton
class ExportRunner @Inject()(exporter: Exporter, csvExporter: CSVExporter)(implicit ec: ExecutionContext) {

  private val logger =
    Logger(this.getClass)

  private val owner = "hibobio"
  private val ownersWithRepos =
    List(
      OwnerWithRepo(owner, "hibob"),
      OwnerWithRepo(owner, "social"),
      OwnerWithRepo(owner, "surveys"),
      OwnerWithRepo(owner, "docs"),
      OwnerWithRepo(owner, "hibob-web"),
    )

  private val fileName = Paths.get("target/pull_requests_export.csv")

  private def export() =
    Future
      .foldLeft[List[PullRequestWithReviewers], List[PullRequestWithReviewers]](
        ownersWithRepos.map(exporter.getAll)
      )(Nil)(_ ++ _)
      .map(createCSV)

  private def createCSV(prs: List[PullRequestWithReviewers]) =
    csvExporter.writeCsvFile(
      fileName,
      List("Repo", "Title", "User", "Url", "Creation Date", "Merged Date", "Requested reviewer", "Approved"),
      prs.map(pr => {
        val approved = pr.reviewres.exists(_.state == "APPROVED").toString
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
             approved)
      })
    )

  // This code is called when the application starts.
  export().map(_ => {
    logger.info("Finished exporting")
    println("Finished exporting")
  })

}

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

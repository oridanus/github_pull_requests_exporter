package services

import java.io.{BufferedWriter, FileWriter}

import com.github.tototoshi.csv.CSVWriter
import javax.inject._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

@Singleton
class ExportRunner @Inject()(exporter: Exporter, csvExporter: CSVExporter)(implicit ec: ExecutionContext) {

  // This code is called when the application starts.

  private val logger = Logger(this.getClass)

  for {
    _ <- export("hibob")
    _ <-   export("social")
    _ <-   export("surveys")
    _ <-   export("docs")
    _ = logger.info("finished")
  } yield true

  def export(repo: String): Future[Boolean]= {

    def createCSV(prs: List[PullRequestWithReviewers]) =
      csvExporter.writeCsvFile(s"$repo.csv",
      List("Title", "User", "Url", "Creation Date", "Merged Date", "Requested reviewer", "Approved"),
      prs.map(pr => {
        val approved = pr.reviewres.exists(_.state == "APPROVED").toString
        val firstReviewer = pr.pr.requested_reviewers.headOption.map(_.login).orElse(pr.reviewres.headOption.map(_.user.login)).getOrElse("")
        List(pr.pr.title, pr.pr.user.login, pr.pr.html_url, pr.pr.created_at, pr.pr.merged_at.getOrElse(""), firstReviewer, approved)
      }))

    exporter.getAll(repo).map(_.filter(_.pr.merged_at.nonEmpty)).map(createCSV).map(_ => true)
  }

}

@Singleton
class CSVExporter {

  def writeCsvFile(
                    fileName: String,
                    header: List[String],
                    rows: List[List[String]]
                  ): Try[Unit] =
    Try(new CSVWriter(new BufferedWriter(new FileWriter(fileName)))).flatMap((csvWriter: CSVWriter) =>
      Try{
        csvWriter.writeAll(
          header +: rows
        )
        csvWriter.close()
      } match {
        case f @ Failure(_) =>
          // Always return the original failure.  In production code we might
          // define a new exception which wraps both exceptions in the case
          // they both fail, but that is omitted here.
          Try(csvWriter.close()).recoverWith{
            case _ => f
          }
        case success =>
          success
      }
    )


}

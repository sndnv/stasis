package stasis.client.service

import scala.concurrent.Future
import scala.util.Try

import org.apache.pekko.Done

trait ApplicationRuntimeRequirements {
  def validate(): Future[Done]
}

object ApplicationRuntimeRequirements extends ApplicationRuntimeRequirements {
  override def validate(): Future[Done] =
    JavaVersion.validate()

  object JavaVersion {
    val Minimum: Int = 17
    val Actual: Option[Int] = Try(Option(System.getProperty("java.specification.version")).map(_.toInt)).toOption.flatten

    def validate(): Future[Done] =
      validate(actual = Actual, minimum = Minimum)

    def validate(actual: Option[Int], minimum: Int): Future[Done] =
      actual match {
        case Some(actual) if actual >= minimum =>
          Future.successful(Done)

        case Some(actual) =>
          Future.failed(
            new IllegalStateException(
              s"Current JVM version is [${actual.toString}] but at least [${minimum.toString}] is required"
            )
          )

        case None =>
          Future.successful(Done)
      }
  }
}

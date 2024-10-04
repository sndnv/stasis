package stasis.layers.persistence.migration

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.slf4j.Logger

import stasis.layers.persistence.Store

final case class Migration(
  version: Int,
  needed: Migration.Action[Boolean],
  action: Migration.Action[_]
) {
  def run(forStore: Store)(implicit ec: ExecutionContext, log: Logger): Future[Boolean] =
    needed
      .run()
      .flatMap {
        case true =>
          log.info(
            "Migration to version [{}] needed for [{} / {}]; running migration...",
            version,
            forStore.getClass.getSimpleName,
            forStore.name()
          )

          action
            .run()
            .map { _ =>
              log.info(
                "Migration to version [{}] for [{} / {}] completed successfully",
                version,
                forStore.getClass.getSimpleName,
                forStore.name()
              )
              true // migration run
            }

        case false =>
          log.debug(
            "Skipping migration to version [{}] for [{} / {}]; migration not needed",
            version,
            forStore.getClass.getSimpleName,
            forStore.name()
          )
          Future.successful(false) // migration skipped
      }
      .recoverWith { case NonFatal(e) =>
        log.error(
          "Migration to version [{}] for [{} / {}] failed with [{} - {}]",
          version,
          forStore.getClass.getSimpleName,
          forStore.name(),
          e.getClass.getSimpleName,
          e.getMessage
        )
        Future.failed(e)
      }
}

object Migration {
  trait Action[T] {
    def run(): Future[T]
  }

  object Action {
    def apply[T](f: => Future[T]): Action[T] = () => f
  }
}

package stasis.layers.persistence.migration

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.layers.persistence.Store

trait MigrationExecutor {
  def execute(forStore: Store): Future[MigrationResult]
}

object MigrationExecutor {
  class Default()(implicit system: ActorSystem[Nothing]) extends MigrationExecutor {
    import system.executionContext

    private implicit val log: Logger = LoggerFactory.getLogger(MigrationExecutor.getClass.getSimpleName)

    def execute(forStore: Store): Future[MigrationResult] = {
      val migrations = forStore.migrations().sortBy(_.version)

      log.debug(
        "Found [{}] migration(s) for [{} / {}]",
        migrations.length,
        forStore.getClass.getSimpleName,
        forStore.name()
      )

      migrations.foldLeft(Future.successful(MigrationResult(found = migrations.length, executed = 0))) {
        case (collected, current) =>
          for {
            collectedResult <- collected
            latestRun <- current.run(forStore = forStore)
          } yield {
            if (latestRun) {
              collectedResult.copy(executed = collectedResult.executed + 1)
            } else {
              collectedResult
            }
          }
      }
    }
  }

  def apply()(implicit system: ActorSystem[Nothing]): MigrationExecutor =
    new Default()
}

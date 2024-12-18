package stasis.layers.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.persistence.migration.MigrationResult

trait PersistenceProvider { parent =>
  def migrate(): Future[MigrationResult]
  def init(): Future[Done]
  def drop(): Future[Done]

  def combineWith(other: PersistenceProvider)(implicit ec: ExecutionContext): PersistenceProvider =
    new PersistenceProvider {
      override def migrate(): Future[MigrationResult] =
        for {
          parentResult <- parent.migrate()
          otherResult <- other.migrate()
        } yield {
          parentResult + otherResult
        }

      override def init(): Future[Done] = parent.init().flatMap(_ => other.init())

      override def drop(): Future[Done] = parent.drop().flatMap(_ => other.drop())
    }
}

package stasis.layers.persistence

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.persistence.migration.Migration

trait Store {
  def name(): String
  def migrations(): Seq[Migration]
  def init(): Future[Done]
  def drop(): Future[Done]
}

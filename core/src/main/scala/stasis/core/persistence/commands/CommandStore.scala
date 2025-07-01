package stasis.core.persistence.commands

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.core.commands.proto.Command
import io.github.sndnv.layers.persistence.Store

trait CommandStore extends Store { store =>
  def put(command: Command): Future[Done]
  def delete(sequenceId: Long): Future[Boolean]
  def truncate(olderThan: Instant): Future[Done]
  def list(): Future[Seq[Command]]
  def list(forEntity: UUID): Future[Seq[Command]]
  def list(forEntity: UUID, lastSequenceId: Long): Future[Seq[Command]]
}

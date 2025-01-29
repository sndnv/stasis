package stasis.server.persistence.devices

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.commands.proto.Command
import stasis.core.persistence.commands.CommandStore
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class MockCommandStore(
  underlying: KeyValueStore[Long, Command]
)(implicit system: ActorSystem[Nothing])
    extends CommandStore {
  private implicit val ec: ExecutionContext = system.executionContext

  private val counter: AtomicLong = new AtomicLong(0)

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def put(command: Command): Future[Done] = {
    val id = counter.incrementAndGet()
    underlying.put(id, value = command.copy(sequenceId = id))
  }

  override def delete(sequenceId: Long): Future[Boolean] =
    underlying.delete(sequenceId)

  override def truncate(olderThan: Instant): Future[Done] = for {
    old <- underlying.entries.map(_.values.filter(_.created.isBefore(olderThan)).map(_.sequenceId).toSeq)
    _ <- Future.sequence(old.map(underlying.delete))
  } yield {
    Done
  }

  override def list(): Future[Seq[Command]] =
    underlying.entries.map(_.values.toSeq)

  override def list(forEntity: UUID): Future[Seq[Command]] =
    underlying.entries.map(_.values.filter(e => e.target.isEmpty || e.target.contains(forEntity)).toSeq)

  override def list(forEntity: UUID, lastSequenceId: Long): Future[Seq[Command]] =
    underlying.entries.map(
      _.values.filter(e => e.sequenceId > lastSequenceId && (e.target.isEmpty || e.target.contains(forEntity))).toSeq
    )

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockCommandStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): MockCommandStore = MockCommandStore(withMessages = Seq.empty)

  def apply(withMessages: Seq[Command])(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): MockCommandStore = {
    import system.executionContext

    val underlying = MemoryStore[Long, Command](
      s"mock-device-command-message-store-${java.util.UUID.randomUUID()}"
    )

    val _ = Await.result(
      Future.sequence(
        withMessages.zipWithIndex.map(e => underlying.put(e._2 + 1L, e._1.copy(sequenceId = e._2 + 1L)))
      ),
      atMost = 1.second
    )

    new MockCommandStore(underlying)
  }
}

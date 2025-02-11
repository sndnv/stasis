package stasis.core.persistence.commands

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.core.commands.proto
import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandSource
import stasis.core.persistence.{Metrics => CoreMetrics}
import stasis.layers.persistence.migration.Migration
import stasis.layers.persistence.{Metrics => LayersMetrics}
import stasis.layers.telemetry.TelemetryContext

class DefaultCommandStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends CommandStore {
  import profile.api._
  import system.executionContext

  private val storeMetrics = telemetry.metrics[LayersMetrics.Store]
  private val commandMetrics = telemetry.metrics[CoreMetrics.CommandStore]

  private implicit val sourceColumnType: JdbcType[CommandSource] =
    MappedColumnType.base[CommandSource, String](_.name, CommandSource.apply)

  private implicit val parametersColumnType: JdbcType[proto.CommandParameters] =
    MappedColumnType.base[proto.CommandParameters, Array[Byte]](
      _.asMessage.toByteArray,
      proto.CommandParametersMessage.parseFrom(_).toCommandParameters
    )

  private class SlickStore(tag: Tag) extends Table[proto.Command](tag, name) {
    def sequenceId: Rep[Long] = column[Long]("SEQUENCE_ID", O.PrimaryKey, O.AutoInc)
    def source: Rep[CommandSource] = column[CommandSource]("SOURCE")
    def target: Rep[Option[UUID]] = column[Option[UUID]]("TARGET")
    def command: Rep[proto.CommandParameters] = column[proto.CommandParameters]("COMMAND")
    def created: Rep[Instant] = column[Instant]("CREATED")

    def * : ProvenShape[proto.Command] =
      (sequenceId, source, target, command, created) <> ((proto.Command.apply _).tupled, proto.Command.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def put(command: proto.Command): Future[Done] = storeMetrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(command))
      .map { _ =>
        commandMetrics.recordCommand(command)
        Done
      }
  }

  override def delete(sequenceId: Long): Future[Boolean] = storeMetrics.recordDelete(store = name) {
    database
      .run(store.filter(_.sequenceId === sequenceId).delete)
      .map(_ == 1)
  }

  override def truncate(olderThan: Instant): Future[Done] = storeMetrics.recordDelete(store = name) {
    database
      .run(store.filter(_.created < olderThan).delete)
      .map(_ => Done)
  }

  override def list(): Future[Seq[Command]] = storeMetrics.recordList(store = name) {
    database.run(store.result)
  }

  override def list(forEntity: UUID): Future[Seq[Command]] = storeMetrics.recordList(store = name) {
    database.run(store.filter(e => e.target.isEmpty || e.target === forEntity).result)
  }

  override def list(forEntity: UUID, lastSequenceId: Long): Future[Seq[Command]] = storeMetrics.recordList(store = name) {
    database.run(store.filter(e => e.sequenceId > lastSequenceId && (e.target.isEmpty || e.target === forEntity)).result)
  }

  override val migrations: Seq[Migration] = Seq(
    Migration(
      version = 1,
      needed = Migration.Action {
        database.run(slick.jdbc.meta.MTable.getTables(namePattern = name).map(_.headOption.isEmpty))
      },
      action = Migration.Action {
        init()
      }
    )
  )
}

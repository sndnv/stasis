package stasis.client.tracking.state.serdes

import java.util.UUID

import scala.util.Try

import stasis.client.model.proto
import stasis.client.tracking.state.BackupState
import stasis.core.persistence.backends.file.state.StateStore
import stasis.shared.ops.Operation

object BackupStateSerdes {
  implicit val backupSerdes: StateStore.Serdes[Map[Operation.Id, BackupState]] =
    new StateStore.Serdes[Map[Operation.Id, BackupState]] {
      override def serialize(state: Map[Operation.Id, BackupState]): Array[Byte] =
        proto.state
          .BackupStateCollection(
            collection = state.map { case (k, v) => k.toString -> BackupState.toProto(state = v) }
          )
          .toByteArray

      override def deserialize(bytes: Array[Byte]): Try[Map[Operation.Id, BackupState]] =
        proto.state.BackupStateCollection
          .parseFrom(bytes)
          .collection
          .foldLeft(Try(Seq.empty[BackupState])) { case (collected, (k, v)) =>
            for {
              collected <- collected
              state <- BackupState.fromProto(operation = UUID.fromString(k), state = v)
            } yield {
              collected :+ state
            }
          }
          .map(_.map(state => state.operation -> state).toMap)
    }
}

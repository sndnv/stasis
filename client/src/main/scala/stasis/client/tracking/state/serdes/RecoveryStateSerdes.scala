package stasis.client.tracking.state.serdes

import stasis.client.model.proto
import stasis.client.tracking.state.RecoveryState
import stasis.core.persistence.backends.file.state.StateStore
import stasis.shared.ops.Operation

import java.util.UUID
import scala.util.Try

object RecoveryStateSerdes {
  implicit val recoverySerdes: StateStore.Serdes[Map[Operation.Id, RecoveryState]] =
    new StateStore.Serdes[Map[Operation.Id, RecoveryState]] {
      override def serialize(state: Map[Operation.Id, RecoveryState]): Array[Byte] =
        proto.state
          .RecoveryStateCollection(
            collection = state.map { case (k, v) => k.toString -> RecoveryState.toProto(state = v) }
          )
          .toByteArray

      override def deserialize(bytes: Array[Byte]): Try[Map[Operation.Id, RecoveryState]] =
        proto.state.RecoveryStateCollection
          .parseFrom(bytes)
          .collection
          .foldLeft(Try(Seq.empty[RecoveryState])) { case (collected, (k, v)) =>
            for {
              collected <- collected
              state <- RecoveryState.fromProto(operation = UUID.fromString(k), state = v)
            } yield {
              collected :+ state
            }
          }
          .map(_.map(state => state.operation -> state).toMap)
    }
}

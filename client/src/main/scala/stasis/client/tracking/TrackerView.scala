package stasis.client.tracking

import java.time.Instant

import stasis.shared.ops.Operation

import scala.collection.immutable.Queue
import scala.concurrent.Future

trait TrackerView {
  def state: Future[TrackerView.State]
}

object TrackerView {
  final case class State(
    operations: Map[Operation.Id, Operation.Progress],
    servers: Map[String, ServerState]
  ) {
    def withServer(server: String, reachable: Boolean): State =
      copy(servers = servers + (server -> ServerState(reachable = reachable, Instant.now())))

    def withStep(operationId: Operation.Id, stage: String, step: String): State = {
      val existingProgress = operations.getOrElse(operationId, Operation.Progress.empty)
      val existingStage = existingProgress.stages.getOrElse(stage, Operation.Progress.Stage(steps = Queue.empty))

      val updatedStage = existingStage.withStep(Operation.Progress.Stage.Step(name = step, completed = Instant.now()))
      val updatedProgress = existingProgress.copy(stages = existingProgress.stages + (stage -> updatedStage))

      copy(operations = operations + (operationId -> updatedProgress))
    }

    def withFailure(operationId: Operation.Id, failure: Throwable): State = {
      val existingProgress = operations.getOrElse(operationId, Operation.Progress.empty)
      val updatedProgress = existingProgress.copy(failures = existingProgress.failures :+ failure.getMessage)

      copy(operations = operations + (operationId -> updatedProgress))
    }

    def completed(operationId: Operation.Id): State = {
      val existingProgress = operations.getOrElse(operationId, Operation.Progress.empty)
      val updatedProgress = existingProgress.copy(completed = Some(Instant.now()))

      copy(operations = operations + (operationId -> updatedProgress))
    }
  }

  object State {
    def empty: State = State(operations = Map.empty, servers = Map.empty)
  }

  final case class ServerState(reachable: Boolean, timestamp: Instant)
}

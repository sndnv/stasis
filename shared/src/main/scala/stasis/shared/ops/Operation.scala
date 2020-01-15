package stasis.shared.ops

import java.time.Instant

import akka.Done

import scala.collection.immutable.Queue
import scala.concurrent.Future

trait Operation {
  def id: Operation.Id
  def start(): Future[Done]
  def stop(): Unit
  def `type`: Operation.Type
}

object Operation {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  sealed trait Type
  object Type {
    sealed trait ClientBased extends Type
    sealed trait ServerBased extends Type

    case object Backup extends ClientBased
    case object Recovery extends ClientBased
    case object Expiration extends ClientBased
    case object Validation extends ClientBased
    case object KeyRotation extends ClientBased

    case object GarbageCollection extends ServerBased
  }

  final case class Progress(
    stages: Map[String, Progress.Stage],
    failures: Queue[String],
    completed: Option[Instant]
  )

  object Progress {
    def empty: Progress =
      Progress(
        failures = Queue.empty,
        stages = Map.empty,
        completed = None
      )

    final case class Stage(steps: Queue[Stage.Step]) {
      def withStep(step: Stage.Step): Stage = copy(steps = steps :+ step)
    }

    object Stage {
      final case class Step(name: String, completed: Instant)
    }
  }
}

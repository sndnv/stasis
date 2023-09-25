package stasis.shared.ops

import akka.Done

import java.time.Instant
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
    started: Instant,
    total: Int,
    processed: Int,
    failures: Int,
    completed: Option[Instant]
  )
}

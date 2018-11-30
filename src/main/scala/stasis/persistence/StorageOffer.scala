package stasis.persistence

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

case class StorageOffer(
  id: StorageOffer.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration
)

object StorageOffer {
  type Id = UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  sealed trait Response
  object Response {
    case object Accepted extends Response
    case object Rejected extends Response
  }
}

package stasis.shared.model.datasets

import scala.concurrent.duration.FiniteDuration

import stasis.shared.model.devices.Device

final case class DatasetDefinition(
  id: DatasetDefinition.Id,
  info: String,
  device: Device.Id,
  redundantCopies: Int,
  existingVersions: DatasetDefinition.Retention,
  removedVersions: DatasetDefinition.Retention
)

object DatasetDefinition {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  final case class Retention(
    policy: Retention.Policy,
    duration: FiniteDuration
  )

  object Retention {
    sealed trait Policy
    object Policy {
      final case class AtMost(versions: Int) extends Policy
      case object LatestOnly extends Policy
      case object All extends Policy
    }
  }
}

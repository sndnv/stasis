package stasis.server.model.datasets

import stasis.server.model.schedules.Schedule
import stasis.server.model.devices.Device

import scala.concurrent.duration.FiniteDuration

final case class DatasetDefinition(
  id: DatasetDefinition.Id,
  device: Device.Id,
  schedule: Option[Schedule.Id],
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

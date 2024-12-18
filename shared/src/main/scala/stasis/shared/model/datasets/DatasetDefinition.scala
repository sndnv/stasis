package stasis.shared.model.datasets

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import stasis.shared.model.devices.Device

final case class DatasetDefinition(
  id: DatasetDefinition.Id,
  info: String,
  device: Device.Id,
  redundantCopies: Int,
  existingVersions: DatasetDefinition.Retention,
  removedVersions: DatasetDefinition.Retention,
  created: Instant,
  updated: Instant
) {
  require(redundantCopies > 0, "Dataset definition redundant copies must be larger than 0")
}

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
      final case class AtMost(versions: Int) extends Policy {
        require(versions > 0, "Policy versions must be larger than 0")

        override def toString: String =
          s"at-most, versions=${versions.toString}"
      }

      case object LatestOnly extends Policy {
        override def toString: String = "latest-only"
      }

      case object All extends Policy {
        override def toString: String = "all"
      }
    }
  }
}

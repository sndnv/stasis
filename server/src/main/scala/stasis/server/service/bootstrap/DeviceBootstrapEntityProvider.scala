package stasis.server.service.bootstrap

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.{config => typesafe}
import org.apache.pekko.Done

import stasis.layers.service.bootstrap.BootstrapEntityProvider
import stasis.server.persistence.devices.DeviceStore
import stasis.shared.model.devices.Device

class DeviceBootstrapEntityProvider(store: DeviceStore)(implicit ec: ExecutionContext) extends BootstrapEntityProvider[Device] {
  override val name: String = "devices"

  override def default: Seq[Device] =
    Seq.empty

  override def load(config: Config): Device = {
    val now = Instant.now()
    Device(
      id = UUID.fromString(config.getString("id")),
      name = config.getString("name"),
      node = UUID.fromString(config.getString("node")),
      owner = UUID.fromString(config.getString("owner")),
      active = config.getBoolean("active"),
      limits = Try(config.getConfig("limits")).toOption.map(deviceLimitsFromConfig),
      created = now,
      updated = now
    )
  }

  override def validate(entities: Seq[Device]): Future[Done] =
    for {
      _ <- requireNonDuplicateField(entities, _.id)
      _ <- requireNonDuplicateField(entities, _.node)
    } yield {
      Done
    }

  override def create(entity: Device): Future[Done] =
    store.manage().put(entity)

  override def render(entity: Device, withPrefix: String): String =
    s"""
       |$withPrefix  device:
       |$withPrefix    id:                      ${entity.id.toString}
       |$withPrefix    name:                    ${entity.name}
       |$withPrefix    node:                    ${entity.node.toString}
       |$withPrefix    owner:                   ${entity.owner.toString}
       |$withPrefix    active:                  ${entity.active.toString}
       |$withPrefix    limits:
       |$withPrefix      max-crates:            ${entity.limits.map(_.maxCrates.toString).getOrElse("-")}
       |$withPrefix      max-storage:           ${entity.limits.map(_.maxStorage.toString).getOrElse("-")}
       |$withPrefix      max-storage-per-crate: ${entity.limits.map(_.maxStoragePerCrate.toString).getOrElse("-")}
       |$withPrefix      max-retention:         ${entity.limits.map(_.maxRetention.toCoarsest.toString).getOrElse("-")}
       |$withPrefix      min-retention:         ${entity.limits.map(_.minRetention.toCoarsest.toString).getOrElse("-")}
       |$withPrefix    created:                 ${entity.created.toString}
       |$withPrefix    updated:                 ${entity.updated.toString}""".stripMargin

  override def extractId(entity: Device): String =
    entity.id.toString

  private def deviceLimitsFromConfig(config: typesafe.Config): Device.Limits =
    Device.Limits(
      maxCrates = config.getLong("max-crates"),
      maxStorage = config.getMemorySize("max-storage").toBytes,
      maxStoragePerCrate = config.getMemorySize("max-storage-per-crate").toBytes,
      maxRetention = config.getDuration("max-retention").toSeconds.seconds,
      minRetention = config.getDuration("min-retention").toSeconds.seconds
    )
}

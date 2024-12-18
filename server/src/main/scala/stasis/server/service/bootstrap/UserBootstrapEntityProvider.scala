package stasis.server.service.bootstrap

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.{config => typesafe}
import org.apache.pekko.Done

import stasis.layers.service.bootstrap.BootstrapEntityProvider
import stasis.server.persistence.users.UserStore
import stasis.shared.api.Formats
import stasis.shared.model.users.User
import stasis.shared.security.Permission

class UserBootstrapEntityProvider(store: UserStore) extends BootstrapEntityProvider[User] {
  override val name: String = "users"

  override def default: Seq[User] =
    Seq.empty

  override def load(config: Config): User = {
    val now = Instant.now()
    User(
      id = UUID.fromString(config.getString("id")),
      salt = config.getString("salt"),
      active = config.getBoolean("active"),
      limits = Try(config.getConfig("limits")).toOption.map(userLimitsFromConfig),
      permissions = userPermissionsFromConfig(config.getStringList("permissions").asScala.toSeq),
      created = now,
      updated = now
    )
  }

  override def validate(entities: Seq[User]): Future[Done] =
    requireNonDuplicateField(entities, _.id)

  override def create(entity: User): Future[Done] =
    store.manage().put(entity)

  override def render(entity: User, withPrefix: String): String =
    s"""
       |$withPrefix  user:
       |$withPrefix    id:                      ${entity.id.toString}
       |$withPrefix    salt:                    ***
       |$withPrefix    active:                  ${entity.active.toString}
       |$withPrefix    limits:
       |$withPrefix      max-devices:           ${entity.limits.map(_.maxDevices.toString).getOrElse("-")}
       |$withPrefix      max-crates:            ${entity.limits.map(_.maxCrates.toString).getOrElse("-")}
       |$withPrefix      max-storage:           ${entity.limits.map(_.maxStorage.toString).getOrElse("-")}
       |$withPrefix      max-storage-per-crate: ${entity.limits.map(_.maxStoragePerCrate.toString).getOrElse("-")}
       |$withPrefix      max-retention:         ${entity.limits.map(_.maxRetention.toCoarsest.toString).getOrElse("-")}
       |$withPrefix      min-retention:         ${entity.limits.map(_.minRetention.toCoarsest.toString).getOrElse("-")}
       |$withPrefix    permissions:             ${if (entity.permissions.isEmpty) "none"
      else entity.permissions.map(Formats.permissionToString).mkString(", ")}
       |$withPrefix    created:                 ${entity.created.toString}
       |$withPrefix    updated:                 ${entity.updated.toString}""".stripMargin

  override def extractId(entity: User): String =
    entity.id.toString

  private def userLimitsFromConfig(config: typesafe.Config): User.Limits =
    User.Limits(
      maxDevices = config.getLong("max-devices"),
      maxCrates = config.getLong("max-crates"),
      maxStorage = config.getMemorySize("max-storage").toBytes,
      maxStoragePerCrate = config.getMemorySize("max-storage-per-crate").toBytes,
      maxRetention = config.getDuration("max-retention").toSeconds.seconds,
      minRetention = config.getDuration("min-retention").toSeconds.seconds
    )

  private def userPermissionsFromConfig(permissions: Seq[String]): Set[Permission] =
    permissions
      .map(_.toLowerCase.split("-").toList)
      .collect {
        case "view" :: "self" :: Nil         => Permission.View.Self: Permission
        case "view" :: "privileged" :: Nil   => Permission.View.Privileged: Permission
        case "view" :: "public" :: Nil       => Permission.View.Public: Permission
        case "view" :: "service" :: Nil      => Permission.View.Service: Permission
        case "manage" :: "self" :: Nil       => Permission.Manage.Self: Permission
        case "manage" :: "privileged" :: Nil => Permission.Manage.Privileged: Permission
        case "manage" :: "service" :: Nil    => Permission.Manage.Service: Permission
      }
      .toSet
}

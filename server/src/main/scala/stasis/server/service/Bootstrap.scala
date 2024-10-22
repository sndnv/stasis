package stasis.server.service

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.config.ConfigFactory
import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.slf4j.Logger

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.shared.security.Permission

object Bootstrap {
  final case class Entities(
    definitions: Seq[DatasetDefinition],
    devices: Seq[Device],
    schedules: Seq[Schedule],
    users: Seq[User],
    nodes: Seq[Node]
  )

  def run(
    bootstrapConfig: typesafe.Config,
    serverPersistence: ServerPersistence,
    corePersistence: CorePersistence
  )(implicit
    ec: ExecutionContext,
    log: Logger
  ): Future[Done] = {
    val enabled = bootstrapConfig.getBoolean("enabled")
    val configFile = bootstrapConfig.getString("config").trim

    if (enabled && configFile.nonEmpty) {
      val config = ConfigFactory
        .parseFile(
          Option(getClass.getClassLoader.getResource(configFile))
            .map(resource => new File(resource.getFile))
            .getOrElse(new File(configFile))
        )
        .resolve()
        .getConfig("bootstrap")

      val entities = Entities(
        definitions = config.getConfigList("dataset-definitions").asScala.map(definitionFromConfig).toSeq,
        devices = config.getConfigList("devices").asScala.map(deviceFromConfig).toSeq,
        schedules = config.getConfigList("schedules").asScala.map(scheduleFromConfig).toSeq,
        users = config.getConfigList("users").asScala.map(userFromConfig).toSeq,
        nodes = config.getConfigList("nodes").asScala.map(nodeFromConfig).toSeq
      )

      for {
        _ <- run(entities, serverPersistence, corePersistence)
        coreMigrationResult <- corePersistence.migrate()
        serverMigrationResult <- serverPersistence.migrate()
        _ <- corePersistence.startup()
      } yield {
        log.debug("Executed [{}] out of [{}] core migrations", coreMigrationResult.executed, coreMigrationResult.found)
        log.debug("Executed [{}] out of [{}] server migrations", serverMigrationResult.executed, serverMigrationResult.found)
        Done
      }
    } else {
      for {
        coreMigrationResult <- corePersistence.migrate()
        serverMigrationResult <- serverPersistence.migrate()
        _ <- corePersistence.startup()
      } yield {
        log.debug("Executed [{}] out of [{}] core migrations", coreMigrationResult.executed, coreMigrationResult.found)
        log.debug("Executed [{}] out of [{}] server migrations", serverMigrationResult.executed, serverMigrationResult.found)
        Done
      }
    }
  }

  def run(
    entities: Entities,
    serverPersistence: ServerPersistence,
    corePersistence: CorePersistence
  )(implicit ec: ExecutionContext, log: Logger): Future[Done] =
    for {
      _ <- serverPersistence.init()
      _ <- corePersistence.init()
      _ <- Future.sequence(entities.definitions.map(e => logged(serverPersistence.datasetDefinitions.manage().put, e)))
      _ <- Future.sequence(entities.devices.map(e => logged(serverPersistence.devices.manage().put, e)))
      _ <- Future.sequence(entities.schedules.map(e => logged(serverPersistence.schedules.manage().put, e)))
      _ <- Future.sequence(entities.users.map(e => logged(serverPersistence.users.manage().put, e)))
      _ <- Future.sequence(entities.nodes.map(e => logged(corePersistence.nodes.put, e)))
    } yield {
      Done
    }

  private def definitionFromConfig(config: typesafe.Config): DatasetDefinition = {
    val now = Instant.now()
    DatasetDefinition(
      id = UUID.fromString(config.getString("id")),
      info = config.getString("info"),
      device = UUID.fromString(config.getString("device")),
      redundantCopies = config.getInt("redundant-copies"),
      existingVersions = retentionFromConfig(config.getConfig("existing-versions")),
      removedVersions = retentionFromConfig(config.getConfig("removed-versions")),
      created = now,
      updated = now
    )
  }

  private def retentionFromConfig(config: typesafe.Config): DatasetDefinition.Retention =
    DatasetDefinition.Retention(
      policy = config.getString("policy").toLowerCase match {
        case "at-most"     => DatasetDefinition.Retention.Policy.AtMost(config.getInt("policy-versions"))
        case "latest-only" => DatasetDefinition.Retention.Policy.LatestOnly
        case "all"         => DatasetDefinition.Retention.Policy.All
      },
      duration = config.getDuration("duration").toSeconds.seconds
    )

  private def deviceFromConfig(config: typesafe.Config): Device = {
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

  private def deviceLimitsFromConfig(config: typesafe.Config): Device.Limits =
    Device.Limits(
      maxCrates = config.getLong("max-crates"),
      maxStorage = config.getMemorySize("max-storage").toBytes,
      maxStoragePerCrate = config.getMemorySize("max-storage-per-crate").toBytes,
      maxRetention = config.getDuration("max-retention").toSeconds.seconds,
      minRetention = config.getDuration("min-retention").toSeconds.seconds
    )

  private def scheduleFromConfig(config: typesafe.Config): Schedule = {
    val now = Instant.now()
    Schedule(
      id = UUID.fromString(config.getString("id")),
      info = config.getString("info"),
      isPublic = config.getBoolean("public"),
      start = Try(config.getString("start"))
        .filter(_.trim.nonEmpty)
        .toOption
        .map(LocalDateTime.parse)
        .getOrElse(LocalDateTime.now()),
      interval = config.getDuration("interval").toSeconds.seconds,
      created = now,
      updated = now
    )
  }

  private def userFromConfig(config: typesafe.Config): User = {
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

  private def nodeFromConfig(config: typesafe.Config): Node = {
    val now = Instant.now()
    config.getString("type").toLowerCase match {
      case "local" =>
        Node.Local(
          id = UUID.fromString(config.getString("id")),
          storeDescriptor = CrateStore.Descriptor(config.getConfig("store")),
          created = now,
          updated = now
        )

      case "remote-http" =>
        Node.Remote.Http(
          id = UUID.fromString(config.getString("id")),
          address = HttpEndpointAddress(config.getString("address")),
          storageAllowed = config.getBoolean("storage-allowed"),
          created = now,
          updated = now
        )

      case "remote-grpc" =>
        Node.Remote.Grpc(
          id = UUID.fromString(config.getString("id")),
          address = GrpcEndpointAddress(
            host = config.getString("address.host"),
            port = config.getInt("address.port"),
            tlsEnabled = config.getBoolean("address.tls-enabled")
          ),
          storageAllowed = config.getBoolean("storage-allowed"),
          created = now,
          updated = now
        )

    }
  }

  private def logged[T](
    create: T => Future[Done],
    entity: T
  )(implicit ec: ExecutionContext, log: Logger): Future[Done] =
    create(entity)
      .map { result =>
        entity match {
          case definition: DatasetDefinition => log.info("Dataset definition [{}] added", definition.id)
          case device: Device                => log.info("Device [{}] added", device.id)
          case schedule: Schedule            => log.info("Schedule [{}] added", schedule.id)
          case user: User                    => log.info("User [{}] added", user.id)
          case node: Node                    => log.info("Node [{}] added", node.id)
        }

        result
      }
      .recoverWith { case NonFatal(e) =>
        log.error(
          "Failed to add entity [{}]: [{} - {}]",
          entity.getClass.getName,
          e.getClass.getSimpleName,
          e.getMessage
        )
        Future.failed(e)
      }
}

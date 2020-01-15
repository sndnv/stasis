package stasis.test.specs.unit.server.service

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.MINUTES
import java.util.UUID

import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.event.{Logging, LoggingAdapter}
import com.typesafe.config.Config
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.server.service.{Bootstrap, CorePersistence, ServerPersistence}
import stasis.server.service.Bootstrap.Entities
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.{Generators => CoreGenerators}
import stasis.test.specs.unit.shared.model.Generators

import scala.concurrent.duration._

class BootstrapSpec extends AsyncUnitSpec {
  "Bootstrap" should "setup the service with provided entities" in {
    val expectedEntities = Entities(
      definitions = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateDefinition),
      devices = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateDevice),
      schedules = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateSchedule),
      users = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateUser),
      nodes = stasis.test.Generators.generateSeq(min = 1, g = CoreGenerators.generateLocalNode)
    )

    val serverPersistence = new ServerPersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val corePersistence = new CorePersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    Bootstrap
      .run(
        entities = expectedEntities,
        serverPersistence = serverPersistence,
        corePersistence = corePersistence
      )
      .flatMap { _ =>
        for {
          actualDefinitions <- serverPersistence.datasetDefinitions.view().list()
          actualDevices <- serverPersistence.devices.view().list()
          actualSchedules <- serverPersistence.schedules.view().list()
          actualUsers <- serverPersistence.users.view().list()
          actualNodes <- corePersistence.nodes.nodes
          _ <- serverPersistence.drop()
          _ <- corePersistence.drop()
        } yield {
          actualDefinitions.values.toSeq.sortBy(_.id) should be(expectedEntities.definitions.sortBy(_.id))
          actualDevices.values.toSeq.sortBy(_.id) should be(expectedEntities.devices.sortBy(_.id))
          actualSchedules.values.toSeq.sortBy(_.id) should be(expectedEntities.schedules.sortBy(_.id))
          actualUsers.values.toSeq.sortBy(_.id) should be(expectedEntities.users.sortBy(_.id))
          actualNodes.values.toSeq.sortBy(_.id) should be(expectedEntities.nodes.sortBy(_.id))
        }
      }
  }

  it should "setup the service with configured entities" in {
    val serverPersistence = new ServerPersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val corePersistence = new CorePersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val expectedDeviceId = UUID.fromString("9b47ab81-c472-40e6-834e-6ede83f8893b")
    val expectedUserId = UUID.fromString("749d8c0e-6105-4022-ae0e-39bd77752c5d")

    Bootstrap
      .run(
        bootstrapConfig = config.getConfig("bootstrap-enabled"),
        serverPersistence = serverPersistence,
        corePersistence = corePersistence
      )
      .flatMap { _ =>
        for {
          actualDefinitions <- serverPersistence.datasetDefinitions.view().list()
          actualDevices <- serverPersistence.devices.view().list()
          actualSchedules <- serverPersistence.schedules.view().list()
          actualUsers <- serverPersistence.users.view().list()
          actualNodes <- corePersistence.nodes.nodes
          _ <- serverPersistence.drop()
          _ <- corePersistence.drop()
        } yield {
          actualDefinitions.values.toList.sortBy(_.id.toString) match {
            case definition1 :: definition2 :: Nil =>
              definition1.device should be(expectedDeviceId)
              definition1.redundantCopies should be(2)
              definition1.existingVersions should be(
                DatasetDefinition.Retention(
                  policy = DatasetDefinition.Retention.Policy.AtMost(versions = 5),
                  duration = 7.days
                )
              )
              definition1.removedVersions should be(
                DatasetDefinition.Retention(
                  policy = DatasetDefinition.Retention.Policy.LatestOnly,
                  duration = 0.days
                )
              )

              definition2.device should be(expectedDeviceId)
              definition2.redundantCopies should be(1)
              definition2.existingVersions should be(
                DatasetDefinition.Retention(
                  policy = DatasetDefinition.Retention.Policy.All,
                  duration = 7.days
                )
              )
              definition2.removedVersions should be(
                DatasetDefinition.Retention(
                  policy = DatasetDefinition.Retention.Policy.All,
                  duration = 1.day
                )
              )

            case unexpectedResult =>
              fail(s"Unexpected result received: [$unexpectedResult]")
          }

          actualDevices.values.toList.sortBy(_.id.toString) match {
            case device1 :: device2 :: Nil =>
              device1.owner should be(expectedUserId)
              device1.active should be(true)
              device1.limits should be(None)

              device2.id should be(expectedDeviceId)
              device2.owner should be(expectedUserId)
              device2.active should be(true)
              device2.limits should be(
                Some(
                  Device.Limits(
                    maxCrates = 100000,
                    maxStorage = 536870912000L,
                    maxStoragePerCrate = 1073741824L,
                    maxRetention = 90.days,
                    minRetention = 3.days
                  )
                )
              )

            case unexpectedResult =>
              fail(s"Unexpected result received: [$unexpectedResult]")
          }

          actualSchedules.values.toList.sortBy(_.id.toString) match {
            case schedule1 :: schedule2 :: schedule3 :: Nil =>
              schedule1.info should be("test-schedule-01")
              schedule1.start.truncatedTo(MINUTES) should be(LocalDateTime.now().truncatedTo(MINUTES))
              schedule1.interval should be(30.minutes)

              schedule2.info should be("test-schedule-02")
              schedule2.start should be(LocalDateTime.parse("2000-12-31T10:30:00"))
              schedule2.interval should be(12.hours)

              schedule3.info should be("test-schedule-03")
              schedule3.start should be(LocalDateTime.parse("2000-12-31T12:00:00"))
              schedule3.interval should be(1.hour)

            case unexpectedResult =>
              fail(s"Unexpected result received: [$unexpectedResult]")
          }

          actualUsers.values.toList.sortBy(_.id.toString) match {
            case user1 :: user2 :: Nil =>
              user1.active should be(true)
              user1.permissions should be(Set(Permission.View.Self, Permission.View.Public, Permission.Manage.Self))
              user1.limits should be(None)

              user2.id should be(expectedUserId)
              user2.active should be(true)
              user2.permissions should be(
                Set(
                  Permission.View.Self,
                  Permission.View.Privileged,
                  Permission.View.Public,
                  Permission.View.Service,
                  Permission.Manage.Self,
                  Permission.Manage.Privileged,
                  Permission.Manage.Service
                )
              )
              user2.limits should be(
                Some(
                  User.Limits(
                    maxDevices = 10,
                    maxCrates = 100000,
                    maxStorage = 536870912000L,
                    maxStoragePerCrate = 1073741824L,
                    maxRetention = 90.days,
                    minRetention = 3.days
                  )
                )
              )

            case unexpectedResult =>
              fail(s"Unexpected result received: [$unexpectedResult]")
          }

          actualNodes.values.toList.sortBy(_.id.toString) match {
            case (node1: Node.Local) :: (node2: Node.Remote.Http) :: (node3: Node.Remote.Grpc) :: Nil =>
              node1.storeDescriptor match {
                case CrateStore.Descriptor.ForStreamingMemoryBackend(maxSize, name) =>
                  maxSize should be(1024)
                  name should be("test-memory-store")

                case other =>
                  fail(s"Unexpected local node store descriptor found: [$other]")
              }

              node2.address should be(HttpEndpointAddress("http://localhost:1234"))

              node3.address should be(GrpcEndpointAddress(host = "localhost", port = 5678, tlsEnabled = true))

            case unexpectedResult =>
              fail(s"Unexpected result received: [$unexpectedResult]")
          }
        }
      }
  }

  it should "not run if not enabled" in {
    val serverPersistence = new ServerPersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val corePersistence = new CorePersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val _ = corePersistence.init().await

    Bootstrap
      .run(
        bootstrapConfig = config.getConfig("bootstrap-disabled"),
        serverPersistence = serverPersistence,
        corePersistence = corePersistence
      )
      .flatMap { _ =>
        for {
          _ <- serverPersistence.init()
          actualDefinitions <- serverPersistence.datasetDefinitions.view().list()
          actualDevices <- serverPersistence.devices.view().list()
          actualSchedules <- serverPersistence.schedules.view().list()
          actualUsers <- serverPersistence.users.view().list()
          actualNodes <- corePersistence.nodes.nodes
          _ <- serverPersistence.drop()
          _ <- corePersistence.drop()
        } yield {
          actualDefinitions should be(Map.empty)
          actualDevices should be(Map.empty)
          actualSchedules should be(Map.empty)
          actualUsers should be(Map.empty)
          actualNodes should be(Map.empty)
        }
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "BootstrapSpec"
  )

  private implicit val untyped: akka.actor.ActorSystem = system.toUntyped

  private implicit val log: LoggingAdapter = Logging(untyped, this.getClass.getName)

  private val config: Config = system.settings.config.getConfig("stasis.test.server")
}

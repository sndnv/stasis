package stasis.test.specs.unit.server.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.config.Config
import stasis.core.telemetry.TelemetryContext
import stasis.server.service.CorePersistence
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class CorePersistenceSpec extends AsyncUnitSpec {
  "CorePersistence" should "setup staging store based on config" in {
    val persistenceWithStagingStore = new CorePersistence(
      persistenceConfig = config.getConfig("persistence-with-staging")
    )

    persistenceWithStagingStore.staging should be(defined)

    val persistenceWithoutStagingStore = new CorePersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    persistenceWithoutStagingStore.staging should be(empty)
  }

  it should "setup core data stores based on config" in {
    val persistence = new CorePersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val expectedManifest = Generators.generateManifest
    val expectedLocalNode = Generators.generateLocalNode
    val expectedRemoteNode = Generators.generateRemoteHttpNode
    val expectedReservation = Generators.generateReservation

    for {
      _ <- persistence.init()
      _ <- persistence.manifests.put(expectedManifest)
      actualManifest <- persistence.manifests.get(expectedManifest.crate)
      _ <- persistence.nodes.put(expectedLocalNode)
      actualLocalNode <- persistence.nodes.get(expectedLocalNode.id)
      _ <- persistence.nodes.put(expectedRemoteNode)
      actualRemoteNode <- persistence.nodes.get(expectedRemoteNode.id)
      _ <- persistence.reservations.put(expectedReservation)
      actualReservation <- persistence.reservations.get(expectedReservation.id)
      _ <- persistence.drop()
    } yield {
      actualManifest should be(Some(expectedManifest))
      actualLocalNode should be(Some(expectedLocalNode))
      actualRemoteNode should be(Some(expectedRemoteNode))
      actualReservation should be(Some(expectedReservation))
    }
  }

  it should "provide service data stores as resources" in {
    val persistenceWithStagingStore = new CorePersistence(
      persistenceConfig = config.getConfig("persistence-with-staging")
    )

    val persistenceWithoutStagingStore = new CorePersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val nodes = 3 // manage + manageSelf + view
    val reservations = 1 // view
    val staging = 2 // manage + view

    persistenceWithStagingStore.resources.size should be(
      nodes + reservations + staging
    )

    persistenceWithoutStagingStore.resources.size should be(
      nodes + reservations
    )
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "CorePersistenceSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config: Config = system.settings.config.getConfig("stasis.test.server")
}

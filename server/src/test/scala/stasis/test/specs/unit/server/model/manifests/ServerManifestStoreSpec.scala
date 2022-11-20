package stasis.test.specs.unit.server.model.manifests

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.telemetry.TelemetryContext
import stasis.server.model.manifests.ServerManifestStore
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockManifestStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class ServerManifestStoreSpec extends AsyncUnitSpec {
  "A ServerManifestStore" should "provide a view resource (service)" in {
    val store = ServerManifestStore(new MockManifestStore())

    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return existing manifests via view resource (service)" in {
    val underlying = new MockManifestStore()
    val store = ServerManifestStore(underlying)

    val manifest = Generators.generateManifest
    underlying.put(manifest).await

    store.view().get(manifest.crate).map { result =>
      result should be(Some(manifest))
    }
  }

  it should "provide management resource (service)" in {
    val store = ServerManifestStore(new MockManifestStore())

    store.manage().requiredPermission should be(Permission.Manage.Service)
  }

  it should "allow deleting manifests via management resource (service)" in {
    val underlying = new MockManifestStore()
    val store = ServerManifestStore(underlying)

    val manifest = Generators.generateManifest
    underlying.put(manifest).await

    for {
      someManifests <- underlying.manifests
      deleted <- store.manage().delete(manifest.crate)
      noManifests <- underlying.manifests
    } yield {
      someManifests should be(Map(manifest.crate -> manifest))
      deleted should be(true)
      noManifests should be(Map.empty)
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServerManifestStoreSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()
}

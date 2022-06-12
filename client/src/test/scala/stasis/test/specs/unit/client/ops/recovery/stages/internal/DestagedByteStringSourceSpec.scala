package stasis.test.specs.unit.client.ops.recovery.stages.internal

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.internal.DestagedByteStringSource
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import scala.util.control.NonFatal

class DestagedByteStringSourceSpec extends AsyncUnitSpec {
  "A DestagedByteStringSource" should "support data stream destaging" in {
    val mockStaging = new MockFileStaging()
    val mockTelemetry = MockClientTelemetryContext()

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = mockStaging,
      compression = MockCompression(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker,
      telemetry = mockTelemetry
    )

    val original = Source.single(ByteString("original"))
    val extended = new DestagedByteStringSource(original)

    extended
      .destage(to = Paths.get("/tmp/file/one"))
      .map { _ =>
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(1)
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
        mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(1)

        mockTelemetry.ops.recovery.entityExamined should be(0)
        mockTelemetry.ops.recovery.entityCollected should be(0)
        mockTelemetry.ops.recovery.entityChunkProcessed should be(1)
        mockTelemetry.ops.recovery.entityProcessed should be(0)
        mockTelemetry.ops.recovery.metadataApplied should be(0)
      }
  }

  it should "discard temporary staging files on failure" in {
    val mockStaging = new MockFileStaging()
    val mockTelemetry = MockClientTelemetryContext()

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = mockStaging,
      compression = MockCompression(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker,
      telemetry = mockTelemetry
    )

    val original = Source.failed(new RuntimeException("test failure"))
    val extended = new DestagedByteStringSource(original)

    extended
      .destage(to = Paths.get("/tmp/file/one"))
      .map { _ =>
        fail("Unexpected successful result received")
      }
      .recoverWith { case NonFatal(_) =>
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(1)
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(1)
        mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

        mockTelemetry.ops.recovery.entityExamined should be(0)
        mockTelemetry.ops.recovery.entityCollected should be(0)
        mockTelemetry.ops.recovery.entityChunkProcessed should be(0)
        mockTelemetry.ops.recovery.entityProcessed should be(0)
        mockTelemetry.ops.recovery.metadataApplied should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DestagedByteStringSourceSpec")
}

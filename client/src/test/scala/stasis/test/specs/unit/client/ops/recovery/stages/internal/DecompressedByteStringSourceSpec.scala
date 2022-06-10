package stasis.test.specs.unit.client.ops.recovery.stages.internal

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.internal.DecompressedByteStringSource
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

class DecompressedByteStringSourceSpec extends AsyncUnitSpec with Eventually {
  "A DecompressedByteStringSource" should "support data stream decompression" in {
    val mockTelemetry = MockClientTelemetryContext()

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = new MockFileStaging(),
      decompressor = new MockCompression(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker,
      telemetry = mockTelemetry
    )

    val original = Source.single(ByteString("original"))
    val extended = new DecompressedByteStringSource(original)

    extended
      .decompress()
      .runWith(Sink.head)
      .map { decompressed =>
        decompressed should be(ByteString("decompressed"))

        eventually[Assertion] {
          mockTelemetry.ops.recovery.entityExamined should be(0)
          mockTelemetry.ops.recovery.entityCollected should be(0)
          mockTelemetry.ops.recovery.entityChunkProcessed should be(1)
          mockTelemetry.ops.recovery.entityProcessed should be(0)
          mockTelemetry.ops.recovery.metadataApplied should be(0)
        }
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DecompressedByteStringSourceSpec")
}

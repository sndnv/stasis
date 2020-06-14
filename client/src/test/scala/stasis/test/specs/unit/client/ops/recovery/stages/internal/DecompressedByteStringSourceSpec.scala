package stasis.test.specs.unit.client.ops.recovery.stages.internal

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.internal.DecompressedByteStringSource
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

class DecompressedByteStringSourceSpec extends AsyncUnitSpec {
  "A DecompressedByteStringSource" should "support data stream decompression" in {
    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = new MockFileStaging(),
      decompressor = new MockCompression(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker
    )

    val original = Source.single(ByteString("original"))
    val extended = new DecompressedByteStringSource(original)

    extended
      .decompress()
      .runWith(Sink.head)
      .map { compressed =>
        compressed should be(ByteString("decompressed"))
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DecompressedByteStringSourceSpec")
}

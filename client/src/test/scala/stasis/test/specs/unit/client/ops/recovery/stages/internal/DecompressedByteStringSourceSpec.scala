package stasis.test.specs.unit.client.ops.recovery.stages.internal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.Eventually
import stasis.client.ops.recovery.stages.internal.DecompressedByteStringSource
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

class DecompressedByteStringSourceSpec extends AsyncUnitSpec with Eventually {
  "A DecompressedByteStringSource" should "support data stream decompression" in {
    val original = Source.single(ByteString("original"))
    val extended = new DecompressedByteStringSource(original)

    extended
      .decompress(MockCompression())
      .runWith(Sink.head)
      .map { decompressed =>
        decompressed should be(ByteString("decompressed"))
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DecompressedByteStringSourceSpec")
}

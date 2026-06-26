package stasis.test.specs.unit.client.ops.backup.stages.internal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.ops.backup.stages.internal.CompressedByteStringSource
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

class CompressedByteStringSourceSpec extends AsyncUnitSpec {
  "A CompressedByteStringSource" should "support data stream compression" in {
    val original = Source
      .single(ByteString("original"))

    val extended = new CompressedByteStringSource(original)

    extended
      .compress(new MockCompression())
      .runWith(Sink.head)
      .map { compressed =>
        compressed should be(ByteString("compressed"))
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "CompressedByteStringSourceSpec")
}

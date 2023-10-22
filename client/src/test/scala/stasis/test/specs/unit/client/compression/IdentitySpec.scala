package stasis.test.specs.unit.client.compression

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import stasis.client.compression.Identity
import stasis.test.specs.unit.AsyncUnitSpec

class IdentitySpec extends AsyncUnitSpec {
  "An Identity encoder/decoder implementation" should "provide its name" in {
    Identity.name should be("none")
  }

  it should "skip compression" in {
    Source
      .single(ByteString(data))
      .via(Identity.compress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualCompressedData =>
        actualCompressedData.utf8String should be(data)
      }
  }

  it should "skip decompression" in {
    Source
      .single(ByteString(data))
      .via(Identity.decompress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualDecompressedData =>
        actualDecompressedData.utf8String should be(data)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "IdentitySpec")

  private val data = "some-data"
}

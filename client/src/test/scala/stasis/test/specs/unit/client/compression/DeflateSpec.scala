package stasis.test.specs.unit.client.compression

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.compression.Deflate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class DeflateSpec extends AsyncUnitSpec with EncodingHelpers {
  "A Deflate encoder/decoder implementation" should "provide its name" in {
    Deflate.name should be("deflate")
  }

  it should "compress data" in {
    Source
      .single(ByteString(decompressedData))
      .via(Deflate.compress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualCompressedData =>
        actualCompressedData should be(compressedData.decodeFromBase64)
      }
  }

  it should "decompress data" in {
    Source
      .single(compressedData.decodeFromBase64)
      .via(Deflate.decompress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualDecompressedData =>
        actualDecompressedData.utf8String should be(decompressedData)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DeflateSpec")

  private val decompressedData = "some-decompressed-data"
  private val compressedData = "eNoqzs9N1U1JTc7PLShKLS5OTdFNSSxJBAAAAP//AwBkTwin"
}

package stasis.test.specs.unit.client.compression

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.compression.Gzip
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class GzipSpec extends AsyncUnitSpec with EncodingHelpers {
  "A Gzip encoder/decoder implementation" should "provide its name" in {
    Gzip.name should be("gzip")
  }

  it should "compress data" in {
    Source
      .single(ByteString(decompressedData))
      .via(Gzip.compress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualCompressedData =>
        actualCompressedData should be(compressedData.decodeFromBase64)
      }
  }

  it should "decompress data" in {
    Source
      .single(compressedData.decodeFromBase64)
      .via(Gzip.decompress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualDecompressedData =>
        actualDecompressedData.utf8String should be(decompressedData)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "GzipSpec")

  private val decompressedData = "some-decompressed-data"
  private val compressedData = "H4sIAAAAAAAAACrOz03VTUlNzs8tKEotLk5N0U1JLEkEAAAA//8DAG894xUWAAAA"
}

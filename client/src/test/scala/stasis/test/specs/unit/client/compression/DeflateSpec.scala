package stasis.test.specs.unit.client.compression

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.compression.Deflate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class DeflateSpec extends AsyncUnitSpec with EncodingHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "DeflateSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val decompressedData = "some-decompressed-data"
  private val compressedData = "eNoqzs9N1U1JTc7PLShKLS5OTdFNSSxJBAAAAP//AwBkTwin"

  private val deflate = new Deflate()

  "A Deflate encoder/decoder implementation" should "compress data" in {
    Source
      .single(ByteString(decompressedData))
      .via(deflate.compress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualCompressedData =>
        actualCompressedData should be(compressedData.decodeFromBase64)
      }
  }

  it should "decompress data" in {
    Source
      .single(compressedData.decodeFromBase64)
      .via(deflate.decompress)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualDecompressedData =>
        actualDecompressedData.utf8String should be(decompressedData)
      }
  }
}
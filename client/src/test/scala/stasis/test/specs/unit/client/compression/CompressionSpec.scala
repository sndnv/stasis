package stasis.test.specs.unit.client.compression

import stasis.client.compression.{Compression, Deflate, Gzip}
import stasis.test.specs.unit.UnitSpec

class CompressionSpec extends UnitSpec {
  "Compression" should "provide encoder/decoder implementations based on config" in {
    Compression(compression = "deflate") should be(Deflate)
    Compression(compression = "gzip") should be(Gzip)
  }
}

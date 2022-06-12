package stasis.test.specs.unit.client.compression

import stasis.client.compression.{Compression, Deflate, Gzip, Identity}
import stasis.client.model.{SourceEntity, TargetEntity}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import java.nio.file.Paths

class CompressionSpec extends UnitSpec {
  "Compression" should "provide new instances based on config" in {
    val compression = Compression(withDefaultCompression = "gzip", withDisabledExtensions = "a, b, c")

    compression.defaultCompression should be(Gzip)
    compression.disabledExtensions should be(Set("a", "b", "c"))
  }

  it should "provide encoder/decoder implementations based on config" in {
    Compression.fromString(compression = "deflate") should be(Deflate)
    Compression.fromString(compression = "gzip") should be(Gzip)
    Compression.fromString(compression = "none") should be(Identity)

    an[IllegalArgumentException] should be thrownBy Compression.fromString("other")
  }

  it should "determine compression algorithms based on entity path" in {
    val compression = Compression(withDefaultCompression = "gzip", withDisabledExtensions = "ext1,ext2,ext3")

    compression.algorithmFor(Paths.get("/tmp/file1")) should be("gzip")
    compression.algorithmFor(Paths.get("/tmp/file1.ext")) should be("gzip")
    compression.algorithmFor(Paths.get("/tmp/file1.ext1.ext")) should be("gzip")
    compression.algorithmFor(Paths.get("/tmp/file1.ext1")) should be("none")
    compression.algorithmFor(Paths.get("/tmp/file1.ext2")) should be("none")
    compression.algorithmFor(Paths.get("/tmp/file1.ext3")) should be("none")
    compression.algorithmFor(Paths.get("/tmp/file1ext1")) should be("gzip")
    compression.algorithmFor(Paths.get("/tmp/file1ext2")) should be("gzip")
    compression.algorithmFor(Paths.get("/tmp/file1ext3")) should be("gzip")
  }

  it should "provide encoders for source entities" in {
    val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

    compression.encoderFor(
      entity = SourceEntity(
        path = Paths.get("/tmp/a"),
        existingMetadata = None,
        currentMetadata = Fixtures.Metadata.FileOneMetadata
      )
    ) should be(Identity)

    compression.encoderFor(
      entity = SourceEntity(
        path = Paths.get("/tmp/a"),
        existingMetadata = None,
        currentMetadata = Fixtures.Metadata.FileTwoMetadata
      )
    ) should be(Gzip)

    compression.encoderFor(
      entity = SourceEntity(
        path = Paths.get("/tmp/a"),
        existingMetadata = None,
        currentMetadata = Fixtures.Metadata.FileThreeMetadata
      )
    ) should be(Deflate)
  }

  it should "fail to provide encoders for directories" in {
    val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

    an[IllegalArgumentException] should be thrownBy compression.encoderFor(
      entity = SourceEntity(
        path = Paths.get("/tmp/a"),
        existingMetadata = None,
        currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
      )
    )
  }

  it should "provide decoders for target entities" in {
    val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

    compression.decoderFor(
      entity = TargetEntity(
        path = Paths.get("/tmp/a"),
        destination = TargetEntity.Destination.Default,
        existingMetadata = Fixtures.Metadata.FileOneMetadata,
        currentMetadata = None
      )
    ) should be(Identity)

    compression.decoderFor(
      entity = TargetEntity(
        path = Paths.get("/tmp/a"),
        destination = TargetEntity.Destination.Default,
        existingMetadata = Fixtures.Metadata.FileTwoMetadata,
        currentMetadata = None
      )
    ) should be(Gzip)

    compression.decoderFor(
      entity = TargetEntity(
        path = Paths.get("/tmp/a"),
        destination = TargetEntity.Destination.Default,
        existingMetadata = Fixtures.Metadata.FileThreeMetadata,
        currentMetadata = None
      )
    ) should be(Deflate)
  }

  it should "fail to provide decoders for directories" in {
    val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "a,b,c")

    an[IllegalArgumentException] should be thrownBy compression.decoderFor(
      entity = TargetEntity(
        path = Paths.get("/tmp/a"),
        destination = TargetEntity.Destination.Default,
        existingMetadata = Fixtures.Metadata.DirectoryTwoMetadata,
        currentMetadata = None
      )
    )
  }
}

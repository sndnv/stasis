package stasis.test.specs.unit.persistence.backends.file.container.headers

import java.nio.ByteOrder
import java.util.UUID

import stasis.persistence.backends.file.container.headers.ChunkHeader
import stasis.persistence.backends.file.container.ops.ConversionOps
import stasis.test.specs.unit.UnitSpec

class ChunkHeaderSpec extends UnitSpec {
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  "A ChunkHeader" should "have a fixed size" in {
    ChunkHeader.HEADER_SIZE should be(32)
  }

  it should "convert chunk headers to and from bytes" in {
    val maxHeader = ChunkHeader(
      crateId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      chunkId = Int.MaxValue,
      chunkSize = Int.MaxValue
    )

    val maxHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 127, -1, -1, -1, 127, -1, -1, -1, 0,
      0, 0, 0, 35, 111, 8, 85
    )

    val minHeader = ChunkHeader(
      crateId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      chunkId = Int.MinValue,
      chunkSize = Int.MinValue
    )

    val minHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, -128, 0, 0, 0, -128, 0, 0, 0, 0, 0,
      0, 0, 103, 9, 8, 32
    )

    val zeroHeader = ChunkHeader(
      crateId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      chunkId = 0,
      chunkSize = 0
    )

    val zeroHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      -39, 107, 59, -63
    )

    ChunkHeader.toBytes(maxHeader) should be(maxHeaderAsBytes)
    ChunkHeader.toBytes(minHeader) should be(minHeaderAsBytes)
    ChunkHeader.toBytes(zeroHeader) should be(zeroHeaderAsBytes)

    ChunkHeader.fromBytes(maxHeaderAsBytes.toArray) should be(Right(maxHeader))
    ChunkHeader.fromBytes(minHeaderAsBytes.toArray) should be(Right(minHeader))
    ChunkHeader.fromBytes(zeroHeaderAsBytes.toArray) should be(Right(zeroHeader))
  }
}

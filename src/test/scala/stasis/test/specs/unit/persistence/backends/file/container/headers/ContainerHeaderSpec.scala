package stasis.test.specs.unit.persistence.backends.file.container.headers

import java.nio.ByteOrder
import java.util.UUID

import stasis.persistence.backends.file.container.headers.ContainerHeader
import stasis.persistence.backends.file.container.ops.ConversionOps
import stasis.test.specs.unit.UnitSpec

class ContainerHeaderSpec extends UnitSpec {
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  "A ContainerHeader" should "have a fixed size" in {
    ContainerHeader.HEADER_SIZE should be(34)
  }

  it should "convert container headers to and from bytes" in {
    val maxHeader = ContainerHeader(
      containerId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      containerVersion = ContainerHeader.CURRENT_VERSION,
      maxChunkSize = Int.MaxValue,
      maxChunks = Int.MaxValue
    )

    val maxHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 0, 1, 127, -1, -1, -1, 127, -1, -1,
      -1, 0, 0, 0, 0, -49, 76, -29, 104
    )

    val minHeader = ContainerHeader(
      containerId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      containerVersion = ContainerHeader.CURRENT_VERSION,
      maxChunkSize = Int.MinValue,
      maxChunks = Int.MinValue
    )

    val minHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 0, 1, -128, 0, 0, 0, -128, 0, 0, 0,
      0, 0, 0, 0, -117, 42, -29, 29
    )

    val zeroHeader = ContainerHeader(
      containerId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      containerVersion = ContainerHeader.CURRENT_VERSION,
      maxChunkSize = 0,
      maxChunks = 0
    )

    val zeroHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 53, 72, -48, -4
    )

    ContainerHeader.toBytes(maxHeader) should be(maxHeaderAsBytes)
    ContainerHeader.toBytes(minHeader) should be(minHeaderAsBytes)
    ContainerHeader.toBytes(zeroHeader) should be(zeroHeaderAsBytes)

    ContainerHeader.fromBytes(maxHeaderAsBytes.toArray) should be(Right(maxHeader))
    ContainerHeader.fromBytes(minHeaderAsBytes.toArray) should be(Right(minHeader))
    ContainerHeader.fromBytes(zeroHeaderAsBytes.toArray) should be(Right(zeroHeader))
  }
}

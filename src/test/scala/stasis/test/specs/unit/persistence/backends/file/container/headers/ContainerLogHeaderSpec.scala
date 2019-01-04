package stasis.test.specs.unit.persistence.backends.file.container.headers

import java.nio.ByteOrder
import java.util.UUID

import stasis.persistence.backends.file.container.headers.{ContainerHeader, ContainerLogHeader}
import stasis.persistence.backends.file.container.ops.ConversionOps
import stasis.test.specs.unit.UnitSpec

class ContainerLogHeaderSpec extends UnitSpec {
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  "A ContainerLogHeader" should "have a fixed size" in {
    ContainerLogHeader.HEADER_SIZE should be(42)
  }

  it should "convert container log headers to and from bytes" in {
    val maxHeader = ContainerLogHeader(
      containerId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      logId = UUID.fromString("99bc5c06-3385-4e0a-854c-2c1b20ae668c"),
      containerVersion = ContainerHeader.CURRENT_VERSION
    )

    val maxHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, -103, -68, 92, 6, 51, -123, 78, 10,
      -123, 76, 44, 27, 32, -82, 102, -116, 0, 1, 0, 0, 0, 0, -93, 62, -44, -97
    )

    val minHeader = ContainerLogHeader(
      containerId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      logId = UUID.fromString("99bc5c06-3385-4e0a-854c-2c1b20ae668c"),
      containerVersion = ContainerHeader.CURRENT_VERSION
    )

    val minHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, -103, -68, 92, 6, 51, -123, 78, 10,
      -123, 76, 44, 27, 32, -82, 102, -116, 0, 1, 0, 0, 0, 0, -93, 62, -44, -97
    )

    val zeroHeader = ContainerLogHeader(
      containerId = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      logId = UUID.fromString("99bc5c06-3385-4e0a-854c-2c1b20ae668c"),
      containerVersion = ContainerHeader.CURRENT_VERSION
    )

    val zeroHeaderAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, -103, -68, 92, 6, 51, -123, 78, 10,
      -123, 76, 44, 27, 32, -82, 102, -116, 0, 1, 0, 0, 0, 0, -93, 62, -44, -97
    )

    ContainerLogHeader.toBytes(maxHeader) should be(maxHeaderAsBytes)
    ContainerLogHeader.toBytes(minHeader) should be(minHeaderAsBytes)
    ContainerLogHeader.toBytes(zeroHeader) should be(zeroHeaderAsBytes)

    ContainerLogHeader.fromBytes(maxHeaderAsBytes.toArray) should be(Right(maxHeader))
    ContainerLogHeader.fromBytes(minHeaderAsBytes.toArray) should be(Right(minHeader))
    ContainerLogHeader.fromBytes(zeroHeaderAsBytes.toArray) should be(Right(zeroHeader))
  }
}

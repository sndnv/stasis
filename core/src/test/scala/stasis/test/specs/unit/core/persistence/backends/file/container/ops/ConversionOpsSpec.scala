package stasis.test.specs.unit.core.persistence.backends.file.container.ops

import java.nio.{ByteBuffer, ByteOrder}

import stasis.core.persistence.backends.file.container.exceptions.ConversionFailure
import stasis.core.persistence.backends.file.container.ops.ConversionOps
import stasis.test.specs.unit.UnitSpec

import scala.util.{Failure, Try}

class ConversionOpsSpec extends UnitSpec {
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private final val MOCK_HEADER_SIZE: Int = Seq(
    java.lang.Integer.BYTES,
    java.lang.Long.BYTES,
    java.lang.Double.BYTES,
    ConversionOps.CRC_SIZE
  ).sum

  private case class MockHeader(
    a: Int,
    b: Long,
    c: Double
  )

  private def putHeaderFields(header: MockHeader, buffer: ByteBuffer): Unit =
    buffer
      .putInt(header.a)
      .putLong(header.b)
      .putDouble(header.c)

  private def getHeaderFields(buffer: ByteBuffer): MockHeader =
    MockHeader(
      a = buffer.getInt,
      b = buffer.getLong,
      c = buffer.getDouble
    )

  "ConversionOps" should "convert container headers to and from bytes" in {
    val maxHeader = MockHeader(
      a = Int.MaxValue,
      b = Long.MaxValue,
      c = Double.MaxValue
    )

    val maxHeaderAsBytes = Seq[Byte](
      127, -1, -1, -1, 127, -1, -1, -1, -1, -1, -1, -1, 127, -17, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 47, 30, -58, 124
    )

    val minHeader = MockHeader(
      a = Int.MinValue,
      b = Long.MinValue,
      c = Double.MinValue
    )

    val minHeaderAsBytes = Seq[Byte](
      -128, 0, 0, 0, -128, 0, 0, 0, 0, 0, 0, 0, -1, -17, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 27, 97, -86, -18
    )

    val zeroHeader = MockHeader(
      a = 0,
      b = 0L,
      c = 0.0d
    )

    val zeroHeaderAsBytes = Seq[Byte](
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 15, -43, -101, -115
    )

    ConversionOps.toBytes(buffer => putHeaderFields(maxHeader, buffer), MOCK_HEADER_SIZE) should be(maxHeaderAsBytes)
    ConversionOps.toBytes(buffer => putHeaderFields(minHeader, buffer), MOCK_HEADER_SIZE) should be(minHeaderAsBytes)
    ConversionOps.toBytes(buffer => putHeaderFields(zeroHeader, buffer), MOCK_HEADER_SIZE) should be(zeroHeaderAsBytes)

    ConversionOps.fromBytes(getHeaderFields, maxHeaderAsBytes.toArray, MOCK_HEADER_SIZE) should be(Right(maxHeader))
    ConversionOps.fromBytes(getHeaderFields, minHeaderAsBytes.toArray, MOCK_HEADER_SIZE) should be(Right(minHeader))
    ConversionOps.fromBytes(getHeaderFields, zeroHeaderAsBytes.toArray, MOCK_HEADER_SIZE) should be(Right(zeroHeader))
  }

  it should "fail to serialize headers with invalid parameters" in {
    val header = MockHeader(
      a = 0,
      b = 0L,
      c = 0.0d
    )

    Try(ConversionOps.toBytes(buffer => putHeaderFields(header, buffer), MOCK_HEADER_SIZE - 1)) should be(
      Failure(
        ConversionFailure(
          s"Failed to convert object to bytes: [BufferOverflowException: null]"
        )
      )
    )
  }

  it should "fail to deserialize headers from invalid bytes" in {
    ConversionOps.fromBytes(getHeaderFields, Array[Byte](42), MOCK_HEADER_SIZE) should be(
      Left(
        ConversionFailure(
          s"Failed to convert bytes to object; expected size is [$MOCK_HEADER_SIZE] but [1] byte(s) provided"
        )
      )
    )
  }

  it should "fail to deserialize container headers with invalid CRC" in {
    val invalidCrc = Seq[Byte](0, 0, 0, 0, 47, 30, -58, 125)
    val headerAsBytes = Seq[Byte](
      127, -1, -1, -1, 127, -1, -1, -1, -1, -1, -1, -1, 127, -17, -1, -1, -1, -1, -1, -1
    )

    ConversionOps.fromBytes(getHeaderFields, bytes = (headerAsBytes ++ invalidCrc).toArray, MOCK_HEADER_SIZE) should be(
      Left(
        ConversionFailure(
          "Failed to convert bytes to object; expected CRC [790546044] but found [790546045]"
        )
      )
    )
  }
}

package stasis.persistence.backends.file.container.ops

import java.nio.{ByteBuffer, ByteOrder}
import java.util.zip.CRC32

import stasis.persistence.backends.file.container.exceptions.ConversionFailure

import scala.util.Try

object ConversionOps {
  final val DEFAULT_BYTE_ORDER: ByteOrder = ByteOrder.BIG_ENDIAN

  final val CRC_SIZE: Int = java.lang.Long.BYTES

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def toBytes(
    putObjectFields: ByteBuffer => Unit,
    expectedObjectSize: Int
  )(implicit byteOrder: ByteOrder): Array[Byte] =
    Try {
      val buffer = ByteBuffer.allocate(expectedObjectSize).order(byteOrder)

      putObjectFields(buffer)

      val bytes = buffer.array()
      val crc = calculateCrc(bytes, expectedObjectSize - CRC_SIZE)

      buffer.putLong(crc).array()
    }.toEither match {
      case Left(e)      => throw ConversionFailure(s"Failed to convert object to bytes: [$e]")
      case Right(bytes) => bytes
    }

  def fromBytes[H](
    getObjectFields: ByteBuffer => H,
    bytes: Array[Byte],
    expectedObjectSize: Int
  )(implicit byteOrder: ByteOrder): Either[Throwable, H] =
    if (bytes.lengthCompare(expectedObjectSize) != 0) {
      Left(
        ConversionFailure(
          s"Failed to convert bytes to object; expected size is [$expectedObjectSize] but [${bytes.length}] byte(s) provided"
        )
      )
    } else {
      Try {
        val buffer = ByteBuffer.allocate(expectedObjectSize).order(byteOrder).put(bytes)
        val _ = buffer.flip()

        val obj = getObjectFields(buffer)

        val availableCrc = buffer.getLong
        val expectedCrc = calculateCrc(bytes, expectedObjectSize - CRC_SIZE)

        if (availableCrc != expectedCrc) {
          Left(
            ConversionFailure(
              s"Failed to convert bytes to object; expected CRC [$expectedCrc] but found [$availableCrc]"
            )
          )
        } else {
          Right(obj)
        }
      }.toEither.joinRight
    }

  private def calculateCrc(bytes: Array[Byte], checksumDataSize: Int): Long = {
    val crc = new CRC32()
    crc.update(bytes, 0, checksumDataSize)
    crc.getValue
  }
}

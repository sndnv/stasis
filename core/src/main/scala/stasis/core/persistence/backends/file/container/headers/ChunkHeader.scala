package stasis.core.persistence.backends.file.container.headers

import java.nio.ByteOrder
import java.util.UUID

import stasis.core.persistence.backends.file.container.ops.ConversionOps

final case class ChunkHeader(
  crateId: UUID,
  chunkId: Int,
  chunkSize: Int
)

object ChunkHeader {
  final val HEADER_SIZE: Int =
    Seq(
      java.lang.Long.BYTES, // crateId / most significant bits
      java.lang.Long.BYTES, // crateId / least significant bits
      java.lang.Integer.BYTES, // chunkId
      java.lang.Integer.BYTES, // chunkSize
      ConversionOps.CRC_SIZE // crc
    ).sum

  def toBytes(header: ChunkHeader)(implicit byteOrder: ByteOrder): Array[Byte] =
    ConversionOps.toBytes(
      putObjectFields = { buffer =>
        val _ = buffer
          .putLong(header.crateId.getMostSignificantBits)
          .putLong(header.crateId.getLeastSignificantBits)
          .putInt(header.chunkId)
          .putInt(header.chunkSize)
      },
      expectedObjectSize = HEADER_SIZE
    )

  def fromBytes(bytes: Array[Byte])(implicit byteOrder: ByteOrder): Either[Throwable, ChunkHeader] =
    ConversionOps.fromBytes(
      getObjectFields = { buffer =>
        ChunkHeader(
          crateId = new UUID(buffer.getLong, buffer.getLong),
          chunkId = buffer.getInt,
          chunkSize = buffer.getInt
        )
      },
      bytes = bytes,
      expectedObjectSize = HEADER_SIZE
    )
}

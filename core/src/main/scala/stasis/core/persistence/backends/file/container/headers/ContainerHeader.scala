package stasis.core.persistence.backends.file.container.headers

import java.nio.ByteOrder
import java.util.UUID

import stasis.core.persistence.backends.file.container.ops.ConversionOps

final case class ContainerHeader(
  containerId: UUID,
  containerVersion: Short,
  maxChunkSize: Int,
  maxChunks: Int
)

object ContainerHeader {
  final val CURRENT_VERSION: Short = 1
  final val SUPPORTED_VERSIONS: Set[Short] = Set(CURRENT_VERSION)

  final val HEADER_SIZE: Int =
    Seq(
      java.lang.Long.BYTES, // containerId / most significant bits
      java.lang.Long.BYTES, // containerId / least significant bits
      java.lang.Short.BYTES, // containerVersion
      java.lang.Integer.BYTES, // maxChunkSize
      java.lang.Integer.BYTES, // maxChunks
      ConversionOps.CRC_SIZE // crc
    ).sum

  def apply(
    maxChunkSize: Int,
    maxChunks: Int
  ): ContainerHeader =
    ContainerHeader(
      containerId = UUID.randomUUID(),
      containerVersion = CURRENT_VERSION,
      maxChunkSize = maxChunkSize,
      maxChunks = maxChunks
    )

  def toBytes(header: ContainerHeader)(implicit byteOrder: ByteOrder): Array[Byte] =
    ConversionOps.toBytes(
      putObjectFields = { buffer =>
        val _ = buffer
          .putLong(header.containerId.getMostSignificantBits)
          .putLong(header.containerId.getLeastSignificantBits)
          .putShort(header.containerVersion)
          .putInt(header.maxChunkSize)
          .putInt(header.maxChunks)
      },
      expectedObjectSize = HEADER_SIZE
    )

  def fromBytes(bytes: Array[Byte])(implicit byteOrder: ByteOrder): Either[Throwable, ContainerHeader] =
    ConversionOps.fromBytes(
      getObjectFields = { buffer =>
        val header = ContainerHeader(
          containerId = new UUID(buffer.getLong, buffer.getLong),
          containerVersion = buffer.getShort,
          maxChunkSize = buffer.getInt,
          maxChunks = buffer.getInt
        )

        require(
          SUPPORTED_VERSIONS.contains(header.containerVersion),
          s"Unsupported container version found [${header.containerVersion.toString}]; " +
            s"supported versions are: [${SUPPORTED_VERSIONS.mkString(", ")}]"
        )

        header
      },
      bytes = bytes,
      expectedObjectSize = HEADER_SIZE
    )
}

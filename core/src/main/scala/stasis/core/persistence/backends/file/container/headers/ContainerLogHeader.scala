package stasis.core.persistence.backends.file.container.headers

import java.nio.ByteOrder
import java.util.UUID

import stasis.core.persistence.backends.file.container.ops.ConversionOps

final case class ContainerLogHeader(
  containerId: UUID,
  logId: UUID,
  containerVersion: Short
)

object ContainerLogHeader {
  final val HEADER_SIZE: Int =
    Seq(
      java.lang.Long.BYTES, // containerId / most significant bits
      java.lang.Long.BYTES, // containerId / least significant bits
      java.lang.Long.BYTES, // logId / most significant bits
      java.lang.Long.BYTES, // logId / least significant bits
      java.lang.Short.BYTES, // containerVersion
      ConversionOps.CRC_SIZE // crc
    ).sum

  def apply(container: ContainerHeader): ContainerLogHeader =
    ContainerLogHeader(
      containerId = container.containerId,
      logId = UUID.randomUUID(),
      containerVersion = container.containerVersion
    )

  def toBytes(header: ContainerLogHeader)(implicit byteOrder: ByteOrder): Array[Byte] =
    ConversionOps.toBytes(
      putObjectFields = { buffer =>
        val _ = buffer
          .putLong(header.containerId.getMostSignificantBits)
          .putLong(header.containerId.getLeastSignificantBits)
          .putLong(header.logId.getMostSignificantBits)
          .putLong(header.logId.getLeastSignificantBits)
          .putShort(header.containerVersion)
      },
      expectedObjectSize = HEADER_SIZE
    )

  def fromBytes(bytes: Array[Byte])(implicit byteOrder: ByteOrder): Either[Throwable, ContainerLogHeader] =
    ConversionOps.fromBytes(
      getObjectFields = { buffer =>
        val header = ContainerLogHeader(
          containerId = new UUID(buffer.getLong, buffer.getLong),
          logId = new UUID(buffer.getLong, buffer.getLong),
          containerVersion = buffer.getShort
        )

        require(
          ContainerHeader.SUPPORTED_VERSIONS.contains(header.containerVersion),
          s"Unsupported container version found [${header.containerVersion}]; " +
            s"supported versions are: [${ContainerHeader.SUPPORTED_VERSIONS.mkString(", ")}]"
        )

        header
      },
      bytes = bytes,
      expectedObjectSize = HEADER_SIZE
    )
}

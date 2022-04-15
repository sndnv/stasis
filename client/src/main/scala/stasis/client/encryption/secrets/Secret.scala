package stasis.client.encryption.secrets

import java.nio.ByteBuffer

private[secrets] trait Secret {
  final override def toString: String = s"Secret(${this.getClass.getName})"

  protected final val UUID_SIZE: Int =
    Seq(
      java.lang.Long.BYTES, // most significant bits
      java.lang.Long.BYTES // least significant bits
    ).sum

  protected implicit class UuidToBytes(uuid: java.util.UUID) {
    def toByteBuffer: ByteBuffer =
      ByteBuffer
        .allocate(UUID_SIZE)
        .putLong(uuid.getMostSignificantBits)
        .putLong(uuid.getLeastSignificantBits)

    def toBytes: Array[Byte] = toByteBuffer.array()
  }
}

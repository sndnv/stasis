package stasis.client.encryption.secrets

import org.apache.pekko.util.ByteString
import stasis.client.encryption.Aes
import stasis.client.encryption.stream.CipherStage

final case class DeviceMetadataSecret(
  iv: ByteString,
  private val key: ByteString
) extends Secret {
  def encryption: CipherStage = Aes.encryption(key, iv)
  def decryption: CipherStage = Aes.decryption(key, iv)
}

object DeviceMetadataSecret {
  def apply(iv: ByteString, key: ByteString): DeviceMetadataSecret =
    new DeviceMetadataSecret(iv, key)
}

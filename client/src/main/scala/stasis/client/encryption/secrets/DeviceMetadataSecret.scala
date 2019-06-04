package stasis.client.encryption.secrets

import akka.util.ByteString
import stasis.client.encryption.stream.CipherStage

// doc - never stored or sent externally
final case class DeviceMetadataSecret(
  iv: ByteString,
  private val key: ByteString
) extends Secret {
  def encryption: CipherStage = CipherStage.aesEncryption(key, iv)
  def decryption: CipherStage = CipherStage.aesDecryption(key, iv)
}

object DeviceMetadataSecret {
  def apply(iv: ByteString, key: ByteString): DeviceMetadataSecret =
    new DeviceMetadataSecret(iv, key)
}

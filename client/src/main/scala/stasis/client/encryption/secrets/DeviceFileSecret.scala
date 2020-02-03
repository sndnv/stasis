package stasis.client.encryption.secrets

import java.nio.file.Path

import akka.util.ByteString
import stasis.client.encryption.stream.CipherStage
import stasis.client.encryption.Aes

// doc - never stored or sent externally
final case class DeviceFileSecret(
  file: Path,
  iv: ByteString,
  private val key: ByteString
) extends Secret {
  def encryption: CipherStage = Aes.encryption(key, iv)
  def decryption: CipherStage = Aes.decryption(key, iv)
}

object DeviceFileSecret {
  def apply(file: Path, iv: ByteString, key: ByteString): DeviceFileSecret =
    new DeviceFileSecret(file, iv, key)
}

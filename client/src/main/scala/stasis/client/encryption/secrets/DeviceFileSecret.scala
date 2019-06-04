package stasis.client.encryption.secrets

import java.nio.file.Path

import akka.util.ByteString
import stasis.client.encryption.stream.CipherStage

// doc - never stored or sent externally
final case class DeviceFileSecret(
  file: Path,
  iv: ByteString,
  private val key: ByteString
) extends Secret {
  def encryption: CipherStage = CipherStage.aesEncryption(key, iv)
  def decryption: CipherStage = CipherStage.aesDecryption(key, iv)
}

object DeviceFileSecret {
  def apply(file: Path, iv: ByteString, key: ByteString): DeviceFileSecret =
    new DeviceFileSecret(file, iv, key)
}

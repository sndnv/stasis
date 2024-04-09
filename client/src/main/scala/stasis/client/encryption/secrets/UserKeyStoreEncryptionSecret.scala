package stasis.client.encryption.secrets

import scala.concurrent.Future

import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import stasis.client.encryption.Aes
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig

final case class UserKeyStoreEncryptionSecret(
  user: User.Id,
  iv: ByteString,
  private val key: ByteString
)(implicit target: SecretsConfig)
    extends Secret {

  def encryptDeviceSecret(
    secret: DeviceSecret
  )(implicit mat: Materializer): Future[ByteString] =
    secret.encrypted(Aes.encryption(key, iv))

  def decryptDeviceSecret(
    device: Device.Id,
    encryptedSecret: ByteString
  )(implicit mat: Materializer): Future[DeviceSecret] =
    DeviceSecret.decrypted(
      user = user,
      device = device,
      encryptedSecret = encryptedSecret,
      Aes.decryption(key, iv)
    )
}

object UserKeyStoreEncryptionSecret {
  def apply(
    user: User.Id,
    iv: ByteString,
    key: ByteString
  )(implicit target: SecretsConfig): UserKeyStoreEncryptionSecret =
    new UserKeyStoreEncryptionSecret(user, iv, key)
}

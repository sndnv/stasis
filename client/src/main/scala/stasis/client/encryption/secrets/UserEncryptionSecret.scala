package stasis.client.encryption.secrets

import akka.stream.Materializer
import akka.util.ByteString
import stasis.client.encryption.Aes
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

import scala.concurrent.Future

final case class UserEncryptionSecret(
  user: User.Id,
  iv: ByteString,
  private val key: ByteString
)(implicit target: Secret.Config)
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

object UserEncryptionSecret {
  def apply(
    user: User.Id,
    iv: ByteString,
    key: ByteString
  )(implicit target: Secret.Config): UserEncryptionSecret =
    new UserEncryptionSecret(user, iv, key)
}

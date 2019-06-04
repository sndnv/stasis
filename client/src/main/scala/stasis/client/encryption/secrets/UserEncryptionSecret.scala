package stasis.client.encryption.secrets

import scala.concurrent.Future

import akka.stream.Materializer
import akka.util.ByteString
import stasis.client.encryption.stream.CipherStage
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

// doc - never stored or sent externally
final case class UserEncryptionSecret(
  user: User.Id,
  iv: ByteString,
  private val key: ByteString
)(implicit target: Secret.Config)
    extends Secret {

  def encryptDeviceSecret(
    secret: DeviceSecret
  )(implicit mat: Materializer): Future[ByteString] =
    secret.encrypted(CipherStage.aesEncryption(key, iv))

  def decryptDeviceSecret(
    device: Device.Id,
    encryptedSecret: ByteString
  )(implicit mat: Materializer): Future[DeviceSecret] =
    DeviceSecret.decrypted(
      user = user,
      device = device,
      encryptedSecret = encryptedSecret,
      CipherStage.aesDecryption(key, iv)
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

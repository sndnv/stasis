package stasis.client.encryption.secrets

import at.favre.lib.hkdf.HKDF
import org.apache.pekko.util.ByteString

import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig

final case class UserHashedEncryptionPassword(
  user: User.Id,
  private val hashedPassword: ByteString
)(implicit target: SecretsConfig)
    extends Secret {
  def toLocalEncryptionSecret: UserLocalEncryptionSecret = {
    val salt = user.toBytes
    val keyInfo = ByteString(s"${user.toString}-encryption-key")
    val ivInfo = ByteString(s"${user.toString}-encryption-iv")

    val hkdf = HKDF.fromHmacSha512()

    val prk = hkdf.extract(salt, hashedPassword.toArray)

    val key = hkdf.expand(prk, keyInfo.toArray, target.encryption.deviceSecret.keySize)
    val iv = hkdf.expand(prk, ivInfo.toArray, target.encryption.deviceSecret.ivSize)

    UserLocalEncryptionSecret(user = user, key = ByteString(key), iv = ByteString(iv))
  }

  def toKeyStoreEncryptionSecret: UserKeyStoreEncryptionSecret = {
    val salt = user.toBytes
    val keyInfo = ByteString(s"${user.toString}-key-store-encryption-key")
    val ivInfo = ByteString(s"${user.toString}-key-store-encryption-iv")

    val hkdf = HKDF.fromHmacSha512()

    val prk = hkdf.extract(salt, hashedPassword.toArray)

    val key = hkdf.expand(prk, keyInfo.toArray, target.encryption.deviceSecret.keySize)
    val iv = hkdf.expand(prk, ivInfo.toArray, target.encryption.deviceSecret.ivSize)

    UserKeyStoreEncryptionSecret(user = user, key = ByteString(key), iv = ByteString(iv))
  }
}

object UserHashedEncryptionPassword {
  def apply(
    user: User.Id,
    hashedPassword: ByteString
  )(implicit target: SecretsConfig): UserHashedEncryptionPassword =
    new UserHashedEncryptionPassword(user, hashedPassword)
}

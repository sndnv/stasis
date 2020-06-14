package stasis.client.encryption.secrets

import akka.util.ByteString
import at.favre.lib.crypto.HKDF
import stasis.shared.model.users.User

final case class UserHashedEncryptionPassword(
  user: User.Id,
  private val hashedPassword: ByteString
)(implicit target: Secret.Config)
    extends Secret {
  def toEncryptionSecret: UserEncryptionSecret = {
    val salt = user.toBytes
    val keyInfo = ByteString(s"$user-encryption-key")
    val ivInfo = ByteString(s"$user-encryption-iv")

    val hkdf = HKDF.fromHmacSha512()

    val prk = hkdf.extract(salt, hashedPassword.toArray)

    val key = hkdf.expand(prk, keyInfo.toArray, target.encryption.deviceSecret.keySize)
    val iv = hkdf.expand(prk, ivInfo.toArray, target.encryption.deviceSecret.ivSize)

    UserEncryptionSecret(user = user, key = ByteString(key), iv = ByteString(iv))
  }
}

object UserHashedEncryptionPassword {
  def apply(
    user: User.Id,
    hashedPassword: ByteString
  )(implicit target: Secret.Config): UserHashedEncryptionPassword =
    new UserHashedEncryptionPassword(user, hashedPassword)
}

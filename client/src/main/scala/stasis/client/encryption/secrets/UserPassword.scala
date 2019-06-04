package stasis.client.encryption.secrets

import java.nio.charset.{Charset, StandardCharsets}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import akka.util.ByteString
import stasis.shared.model.users.User

// doc - never stored or sent externally
final case class UserPassword(
  user: User.Id,
  salt: String,
  private val password: String
)(implicit target: Secret.Config)
    extends Secret {
  def toHashedAuthenticationPassword: UserHashedAuthenticationPassword =
    UserHashedAuthenticationPassword(
      user = user,
      hashedPassword = UserPassword.derivePassword(
        password = password,
        salt = s"${target.derivation.authentication.saltPrefix}-authentication-$salt",
        iterations = target.derivation.authentication.iterations,
        derivedKeySize = target.derivation.authentication.secretSize
      )
    )

  def toHashedEncryptionPassword: UserHashedEncryptionPassword =
    UserHashedEncryptionPassword(
      user = user,
      hashedPassword = UserPassword.derivePassword(
        password = password,
        salt = s"${target.derivation.encryption.saltPrefix}-encryption-$salt",
        iterations = target.derivation.encryption.iterations,
        derivedKeySize = target.derivation.encryption.secretSize
      )
    )
}

object UserPassword {
  object Defaults {
    final val Charset: Charset = StandardCharsets.UTF_8
    final val Algorithm: String = "PBKDF2WithHmacSHA512"
  }

  def apply(
    user: User.Id,
    salt: String,
    password: String
  )(implicit target: Secret.Config): UserPassword =
    new UserPassword(user, salt, password)

  def derivePassword(
    password: String,
    salt: String,
    iterations: Int,
    derivedKeySize: Int
  ): ByteString = {
    val spec = new PBEKeySpec(
      password.toCharArray,
      salt.getBytes(Defaults.Charset),
      iterations,
      derivedKeySize * 8
    )

    val derivedPassword = SecretKeyFactory
      .getInstance(Defaults.Algorithm)
      .generateSecret(spec)
      .getEncoded

    ByteString(derivedPassword)
  }
}

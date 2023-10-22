package stasis.client.encryption.secrets

import org.apache.pekko.util.ByteString
import stasis.shared.model.users.User
import stasis.shared.secrets.{DerivedPasswords, SecretsConfig}

import java.nio.charset.StandardCharsets

final case class UserPassword(
  user: User.Id,
  salt: String,
  private val password: Array[Char]
)(implicit target: SecretsConfig)
    extends Secret {
  def toAuthenticationPassword: UserAuthenticationPassword =
    if (target.derivation.authentication.enabled) {
      UserAuthenticationPassword.Hashed(
        user = user,
        hashedPassword = DerivedPasswords.deriveHashedAuthenticationPassword(
          password = password,
          saltPrefix = target.derivation.authentication.saltPrefix,
          salt = salt,
          iterations = target.derivation.authentication.iterations,
          derivedKeySize = target.derivation.authentication.secretSize
        )
      )
    } else {
      UserAuthenticationPassword.Unhashed(
        user = user,
        rawPassword = ByteString.fromString(new String(password), StandardCharsets.UTF_8)
      )
    }

  def toHashedEncryptionPassword: UserHashedEncryptionPassword =
    UserHashedEncryptionPassword(
      user = user,
      hashedPassword = DerivedPasswords.deriveHashedEncryptionPassword(
        password = password,
        saltPrefix = target.derivation.encryption.saltPrefix,
        salt = salt,
        iterations = target.derivation.encryption.iterations,
        derivedKeySize = target.derivation.encryption.secretSize
      )
    )
}

object UserPassword {
  def apply(
    user: User.Id,
    salt: String,
    password: Array[Char]
  )(implicit target: SecretsConfig): UserPassword =
    new UserPassword(user, salt, password)
}

package stasis.shared.secrets

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import org.apache.pekko.util.ByteString

object DerivedPasswords {
  object Defaults {
    final val Charset: Charset = StandardCharsets.UTF_8
    final val Algorithm: String = "PBKDF2WithHmacSHA512"
  }

  def deriveHashedAuthenticationPassword(
    password: Array[Char],
    saltPrefix: String,
    salt: String,
    iterations: Int,
    derivedKeySize: Int
  ): ByteString = derivePassword(
    password = password,
    salt = authenticationSalt(saltPrefix = saltPrefix, salt = salt),
    iterations = iterations,
    derivedKeySize = derivedKeySize
  )

  def deriveHashedEncryptionPassword(
    password: Array[Char],
    saltPrefix: String,
    salt: String,
    iterations: Int,
    derivedKeySize: Int
  ): ByteString = derivePassword(
    password = password,
    salt = encryptionSalt(saltPrefix = saltPrefix, salt = salt),
    iterations = iterations,
    derivedKeySize = derivedKeySize
  )

  private def derivePassword(
    password: Array[Char],
    salt: String,
    iterations: Int,
    derivedKeySize: Int
  ): ByteString = {
    val spec = new PBEKeySpec(
      password,
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

  def encode(hashedPassword: ByteString): String =
    encode(hashedPassword.toArray)

  def digest(password: ByteString): String =
    encode(MessageDigest.getInstance("SHA3-512").digest(password.toArray))

  private def encode(bytes: Array[Byte]): String =
    Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)

  private def authenticationSalt(saltPrefix: String, salt: String): String =
    s"$saltPrefix-authentication-$salt"

  private def encryptionSalt(saltPrefix: String, salt: String): String =
    s"$saltPrefix-encryption-$salt"
}

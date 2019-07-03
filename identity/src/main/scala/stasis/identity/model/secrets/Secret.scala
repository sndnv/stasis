package stasis.identity.model.secrets

import java.nio.charset.StandardCharsets
import java.security.SecureRandom

import akka.util.ByteString
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

final case class Secret(value: ByteString) {
  override def toString: String = "Secret"

  def isSameAs(rawSecret: String, salt: String)(implicit config: Secret.Config): Boolean = {
    val hashedSecret = Secret.derive(rawSecret, salt)

    value == hashedSecret.value
  }
}

object Secret {
  sealed trait Config {
    def algorithm: String
    def iterations: Int
    def derivedKeySize: Int
    def saltSize: Int
    def authenticationDelay: FiniteDuration
  }

  final case class ClientConfig(
    override val algorithm: String,
    override val iterations: Int,
    override val derivedKeySize: Int,
    override val saltSize: Int,
    override val authenticationDelay: FiniteDuration
  ) extends Config

  final case class ResourceOwnerConfig(
    override val algorithm: String,
    override val iterations: Int,
    override val derivedKeySize: Int,
    override val saltSize: Int,
    override val authenticationDelay: FiniteDuration
  ) extends Config

  def apply(value: ByteString): Secret = new Secret(value)

  def derive(
    rawSecret: String,
    salt: String
  )(implicit config: Config): Secret = {
    val spec = new PBEKeySpec(
      rawSecret.toCharArray,
      salt.getBytes(StandardCharsets.UTF_8),
      config.iterations,
      config.derivedKeySize * 8
    )

    val derivedSecret = SecretKeyFactory
      .getInstance(config.algorithm)
      .generateSecret(spec)
      .getEncoded

    Secret(ByteString(derivedSecret))
  }

  def generateSalt()(implicit config: Config): String = {
    val random = Random.javaRandomToRandom(new SecureRandom())
    random.alphanumeric.take(config.saltSize).mkString("")
  }
}

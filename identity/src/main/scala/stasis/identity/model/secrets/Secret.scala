package stasis.identity.model.secrets

import java.nio.charset.StandardCharsets
import java.security.SecureRandom

import org.apache.pekko.util.ByteString
import com.typesafe.{config => typesafe}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import scala.concurrent.duration._
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

  object ClientConfig {
    def apply(clientSecretsConfig: typesafe.Config): ClientConfig =
      ClientConfig(
        algorithm = clientSecretsConfig.getString("algorithm"),
        iterations = clientSecretsConfig.getInt("iterations"),
        derivedKeySize = clientSecretsConfig.getInt("derived-key-size"),
        saltSize = clientSecretsConfig.getInt("salt-size"),
        authenticationDelay = clientSecretsConfig.getDuration("authentication-delay").toMillis.millis
      )
  }

  final case class ResourceOwnerConfig(
    override val algorithm: String,
    override val iterations: Int,
    override val derivedKeySize: Int,
    override val saltSize: Int,
    override val authenticationDelay: FiniteDuration
  ) extends Config

  object ResourceOwnerConfig {
    def apply(ownerSecretsConfig: typesafe.Config): ResourceOwnerConfig =
      ResourceOwnerConfig(
        algorithm = ownerSecretsConfig.getString("algorithm"),
        iterations = ownerSecretsConfig.getInt("iterations"),
        derivedKeySize = ownerSecretsConfig.getInt("derived-key-size"),
        saltSize = ownerSecretsConfig.getInt("salt-size"),
        authenticationDelay = ownerSecretsConfig.getDuration("authentication-delay").toMillis.millis
      )
  }

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

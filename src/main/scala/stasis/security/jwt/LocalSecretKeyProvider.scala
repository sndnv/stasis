package stasis.security.jwt

import java.nio.charset.StandardCharsets
import java.security.Key
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import scala.concurrent.Future

import org.jose4j.jws.AlgorithmIdentifiers
import stasis.security.exceptions.ProviderFailure

final class LocalSecretKeyProvider(
  secret: String,
  algorithm: String,
  override val issuer: String,
  override val allowedAlgorithms: Seq[String]
) extends JwtKeyProvider {
  private val key: SecretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm)

  override def key(id: Option[String]): Future[Key] =
    id match {
      case Some(keyId) => Future.failed(ProviderFailure(s"Key [$keyId] was not expected"))
      case None        => Future.successful(key)
    }
}

object LocalSecretKeyProvider {
  def apply(secret: String, algorithm: String, issuer: String): LocalSecretKeyProvider = new LocalSecretKeyProvider(
    secret,
    algorithm,
    issuer,
    allowedAlgorithms = Seq(
      AlgorithmIdentifiers.HMAC_SHA256,
      AlgorithmIdentifiers.HMAC_SHA384,
      AlgorithmIdentifiers.HMAC_SHA512
    )
  )
}

package stasis.core.security.keys

import java.security.Key

import org.jose4j.jwk._
import stasis.core.security.exceptions.ProviderFailure

import scala.concurrent.Future

final class LocalKeyProvider(
  jwk: JsonWebKey,
  override val issuer: String
) extends KeyProvider {

  private val key: Key = jwk match {
    case key: RsaJsonWebKey           => key.getPublicKey
    case key: EllipticCurveJsonWebKey => key.getPublicKey
    case key: OctetSequenceJsonWebKey => key.getKey
  }

  override def allowedAlgorithms: Seq[String] = Seq(jwk.getAlgorithm)

  override def key(id: Option[String]): Future[Key] =
    id match {
      case Some(keyId) if jwk.getKeyId == keyId => Future.successful(key)
      case Some(keyId)                          => Future.failed(ProviderFailure(s"Key [$keyId] was not expected"))
      case None                                 => Future.successful(key)
    }
}

object LocalKeyProvider {
  def apply(
    jwk: JsonWebKey,
    issuer: String
  ): LocalKeyProvider =
    new LocalKeyProvider(
      jwk = jwk,
      issuer = issuer
    )
}

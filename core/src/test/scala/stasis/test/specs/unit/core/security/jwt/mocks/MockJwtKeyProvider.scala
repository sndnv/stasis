package stasis.test.specs.unit.core.security.jwt.mocks

import java.security.Key

import org.jose4j.jwk.JsonWebKey
import org.jose4j.jws.AlgorithmIdentifiers
import stasis.core.security.jwt.JwtKeyProvider

import scala.concurrent.Future

object MockJwtKeyProvider {
  def apply(jwk: JsonWebKey): JwtKeyProvider = new JwtKeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)

    override def issuer: String = "self"

    override def allowedAlgorithms: Seq[String] = Seq(
      AlgorithmIdentifiers.HMAC_SHA256,
      AlgorithmIdentifiers.HMAC_SHA384,
      AlgorithmIdentifiers.HMAC_SHA512,
      AlgorithmIdentifiers.RSA_USING_SHA256,
      AlgorithmIdentifiers.RSA_USING_SHA384,
      AlgorithmIdentifiers.RSA_USING_SHA512,
      AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256,
      AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384,
      AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512
    )
  }
}

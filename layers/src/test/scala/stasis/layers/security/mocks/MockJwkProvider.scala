package stasis.layers.security.mocks

import java.security.Key

import scala.concurrent.Future

import org.jose4j.jwk.JsonWebKey
import org.jose4j.jws.AlgorithmIdentifiers

import stasis.layers.security.keys.KeyProvider

object MockJwkProvider {
  def apply(jwk: JsonWebKey): KeyProvider =
    new KeyProvider {
      override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)

      override def issuer: String = "self"

      override def allowedAlgorithms: Seq[String] =
        Seq(
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

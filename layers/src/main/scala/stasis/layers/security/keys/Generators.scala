package stasis.layers.security.keys

import java.security.spec.ECParameterSpec

import org.jose4j.jwk._
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.keys.EllipticCurves

object Generators {
  object Algorithms {
    final val Hmac: Map[String, Int] = Map(
      AlgorithmIdentifiers.HMAC_SHA256 -> 256,
      AlgorithmIdentifiers.HMAC_SHA384 -> 384,
      AlgorithmIdentifiers.HMAC_SHA512 -> 512
    )

    final val Rsa: Seq[String] = Seq(
      AlgorithmIdentifiers.RSA_USING_SHA256,
      AlgorithmIdentifiers.RSA_USING_SHA384,
      AlgorithmIdentifiers.RSA_USING_SHA512
    )

    final val EllipticCurve: Map[String, ECParameterSpec] = Map(
      AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256 -> EllipticCurves.P256,
      AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384 -> EllipticCurves.P384,
      AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512 -> EllipticCurves.P521
    )
  }

  def generateRandomSecretKey(keyId: Option[String], algorithm: String): OctetSequenceJsonWebKey = {
    val jwk = OctJwkGenerator.generateJwk(Algorithms.Hmac(algorithm))
    keyId.foreach(id => jwk.setKeyId(id))
    jwk.setAlgorithm(algorithm)
    jwk
  }

  def generateRandomRsaKey(keyId: Option[String], keySize: Int, algorithm: String): RsaJsonWebKey = {
    val jwk = RsaJwkGenerator.generateJwk(keySize)
    keyId.foreach(id => jwk.setKeyId(id))
    jwk.setAlgorithm(algorithm)
    jwk
  }

  def generateRandomEcKey(keyId: Option[String], algorithm: String): EllipticCurveJsonWebKey = {
    val jwk = EcJwkGenerator.generateJwk(Algorithms.EllipticCurve(algorithm))
    keyId.foreach(id => jwk.setKeyId(id))
    jwk.setAlgorithm(algorithm)
    jwk
  }
}

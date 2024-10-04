package stasis.layers.security.mocks

import org.jose4j.jwk.EllipticCurveJsonWebKey
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.OctetSequenceJsonWebKey
import org.jose4j.jwk.RsaJsonWebKey
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims

object MockJwtGenerators {
  def generateJwt(
    issuer: String,
    audience: String,
    subject: String,
    signatureKey: JsonWebKey,
    customClaims: Map[String, String] = Map.empty
  ): String = {

    val jwt = new JwtClaims
    jwt.setIssuer(issuer)
    jwt.setAudience(audience)
    jwt.setExpirationTimeMinutesInTheFuture(1)
    jwt.setGeneratedJwtId()
    jwt.setIssuedAtToNow()
    jwt.setSubject(subject)

    customClaims.foreach { case (name, value) =>
      jwt.setStringClaim(name, value)
    }

    val jws = new JsonWebSignature
    jws.setPayload(jwt.toJson)

    signatureKey match {
      case key: RsaJsonWebKey           => jws.setKey(key.getPrivateKey)
      case key: EllipticCurveJsonWebKey => jws.setKey(key.getPrivateKey)
      case key: OctetSequenceJsonWebKey => jws.setKey(key.getKey)
    }

    jws.setKeyIdHeaderValue(signatureKey.getKeyId)
    jws.setAlgorithmHeaderValue(signatureKey.getAlgorithm)

    jws.getCompactSerialization
  }
}

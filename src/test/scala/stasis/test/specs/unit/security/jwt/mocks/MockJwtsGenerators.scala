package stasis.test.specs.unit.security.jwt.mocks

import org.jose4j.jwk.{EllipticCurveJsonWebKey, JsonWebKey, OctetSequenceJsonWebKey, RsaJsonWebKey}
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims

object MockJwtsGenerators {
  def generateJwt(
    issuer: String,
    audience: String,
    subject: String,
    signingKey: JsonWebKey
  ): String = {

    val jwt = new JwtClaims
    jwt.setIssuer(issuer)
    jwt.setAudience(audience)
    jwt.setExpirationTimeMinutesInTheFuture(1)
    jwt.setGeneratedJwtId()
    jwt.setIssuedAtToNow()
    jwt.setSubject(subject)

    val jws = new JsonWebSignature
    jws.setPayload(jwt.toJson)

    signingKey match {
      case key: RsaJsonWebKey           => jws.setKey(key.getPrivateKey)
      case key: EllipticCurveJsonWebKey => jws.setKey(key.getPrivateKey)
      case key: OctetSequenceJsonWebKey => jws.setKey(key.getKey)
    }

    jws.setKeyIdHeaderValue(signingKey.getKeyId)
    jws.setAlgorithmHeaderValue(signingKey.getAlgorithm)

    jws.getCompactSerialization
  }
}

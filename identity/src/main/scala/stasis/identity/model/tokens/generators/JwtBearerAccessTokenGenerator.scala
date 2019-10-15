package stasis.identity.model.tokens.generators

import org.jose4j.jwk._
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.AccessToken

import scala.concurrent.duration.FiniteDuration

class JwtBearerAccessTokenGenerator(
  issuer: String,
  jwk: JsonWebKey,
  jwtExpiration: FiniteDuration
) extends AccessTokenGenerator {

  override def generate(client: Client, audience: Seq[Client]): AccessToken =
    generateToken(
      subject = client.subject match {
        case Some(subject) => subject
        case None          => client.id.toString
      },
      audience = audience.map(_.id.toString)
    )

  override def generate(owner: ResourceOwner, audience: Seq[Api]): AccessToken =
    generateToken(
      subject = owner.subject match {
        case Some(subject) => subject
        case None          => owner.username
      },
      audience = audience.map(_.id)
    )

  private def generateToken(subject: String, audience: Seq[String]): AccessToken = {
    val claims = new JwtClaims()

    claims.setGeneratedJwtId()

    claims.setIssuer(issuer)
    claims.setSubject(subject)
    claims.setAudience(audience: _*)

    claims.setExpirationTimeMinutesInTheFuture(jwtExpiration.toMinutes)
    claims.setIssuedAtToNow()

    val jws = new JsonWebSignature
    jws.setPayload(claims.toJson)
    jws.setKeyIdHeaderValue(jwk.getKeyId)
    jws.setAlgorithmHeaderValue(jwk.getAlgorithm)

    jwk match {
      case key: RsaJsonWebKey           => jws.setKey(key.getPrivateKey)
      case key: EllipticCurveJsonWebKey => jws.setKey(key.getPrivateKey)
      case key: OctetSequenceJsonWebKey => jws.setKey(key.getKey)
    }

    val jwt = jws.getCompactSerialization

    AccessToken(value = jwt)
  }
}

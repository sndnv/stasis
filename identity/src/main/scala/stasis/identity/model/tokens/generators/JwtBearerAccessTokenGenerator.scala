package stasis.identity.model.tokens.generators

import org.jose4j.jwk._
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.{JwtClaims, NumericDate}
import stasis.identity.model.Seconds
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.{AccessToken, AccessTokenWithExpiration}

import scala.concurrent.duration._

class JwtBearerAccessTokenGenerator(
  issuer: String,
  jwk: JsonWebKey,
  jwtExpiration: FiniteDuration
) extends AccessTokenGenerator {

  override def generate(client: Client, audience: Seq[Client]): AccessTokenWithExpiration =
    generateToken(
      subject = client.subject match {
        case Some(subject) => subject
        case None          => client.id.toString
      },
      audience = audience.map(_.id.toString),
      expiration = jwtExpiration.min(client.tokenExpiration.value.seconds)
    )

  override def generate(owner: ResourceOwner, audience: Seq[Api]): AccessTokenWithExpiration =
    generateToken(
      subject = owner.subject match {
        case Some(subject) => subject
        case None          => owner.username
      },
      audience = audience.map(_.id),
      expiration = jwtExpiration
    )

  private def generateToken(
    subject: String,
    audience: Seq[String],
    expiration: FiniteDuration
  ): AccessTokenWithExpiration = {
    val claims = new JwtClaims()

    claims.setGeneratedJwtId()

    claims.setIssuer(issuer)
    claims.setSubject(subject)
    claims.setAudience(audience: _*)

    val expirationTime = NumericDate.now()
    expirationTime.addSeconds(expiration.toSeconds)
    claims.setExpirationTime(expirationTime)
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

    AccessTokenWithExpiration(
      token = AccessToken(value = jwt),
      expiration = Seconds(expiration.toSeconds)
    )
  }
}

object JwtBearerAccessTokenGenerator {
  def apply(
    issuer: String,
    jwk: JsonWebKey,
    jwtExpiration: FiniteDuration
  ): JwtBearerAccessTokenGenerator =
    new JwtBearerAccessTokenGenerator(
      issuer = issuer,
      jwk = jwk,
      jwtExpiration = jwtExpiration
    )
}

package stasis.test.specs.unit.identity.model.tokens.generators

import org.jose4j.jwk.JsonWebKey
import org.jose4j.jws.JsonWebSignature
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import stasis.identity.model.Seconds
import stasis.identity.model.tokens.generators.JwtBearerAccessTokenGenerator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

trait JwtBearerAccessTokenGeneratorBehaviour { _: AsyncUnitSpec =>
  def jwtBearerAccessTokenGenerator(withKeyType: String, withJwk: JsonWebKey): Unit = {
    val issuer = "some-issuer"
    val jwtExpiration = 30.seconds
    val clientTokenExpiration = 15.seconds
    val generator = new JwtBearerAccessTokenGenerator(issuer, withJwk, jwtExpiration)

    it should s"generate JWTs for clients with custom subject ($withKeyType)" in {
      val client = Generators.generateClient.copy(
        subject = Some("some-subject"),
        tokenExpiration = clientTokenExpiration
      )
      val audience = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateClient)
      val accessToken = generator.generate(client, audience)

      accessToken.expiration should be(clientTokenExpiration: Seconds)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(accessToken.token.value)
      jws.setKey(withJwk.getKey)

      jws.verifySignature() should be(true)

      val payload = Json.parse(jws.getPayload).as[JsObject]
      payload.fields should contain("iss" -> JsString(issuer))
      payload.fields should contain("sub" -> JsString(client.subject.getOrElse("invalid")))
      payload.fields should (
        contain(
          "aud" -> JsArray(audience.map(aud => JsString(aud.id.toString)))
        ) or contain(
          "aud" -> JsString(audience.headOption.map(_.id.toString).getOrElse(""))
        )
      )
    }

    it should s"generate JWTs for clients without custom subject ($withKeyType)" in {
      val client = Generators.generateClient.copy(
        subject = None,
        tokenExpiration = clientTokenExpiration
      )
      val audience = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateClient)
      val accessToken = generator.generate(client, audience)

      accessToken.expiration should be(clientTokenExpiration: Seconds)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(accessToken.token.value)
      jws.setKey(withJwk.getKey)

      jws.verifySignature() should be(true)

      val payload = Json.parse(jws.getPayload).as[JsObject]
      payload.fields should contain("iss" -> JsString(issuer))
      payload.fields should contain("sub" -> JsString(client.id.toString))
      payload.fields should (
        contain(
          "aud" -> JsArray(audience.map(aud => JsString(aud.id.toString)))
        ) or contain(
          "aud" -> JsString(audience.headOption.map(_.id.toString).getOrElse(""))
        )
      )
    }

    it should s"generate JWTs for resource owners with custom subject ($withKeyType)" in {
      val owner = Generators.generateResourceOwner.copy(subject = Some("some-subject"))
      val audience = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateApi)
      val accessToken = generator.generate(owner, audience)

      accessToken.expiration should be(jwtExpiration: Seconds)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(accessToken.token.value)
      jws.setKey(withJwk.getKey)

      jws.verifySignature() should be(true)

      val payload = Json.parse(jws.getPayload).as[JsObject]
      payload.fields should contain("iss" -> JsString(issuer))
      payload.fields should contain("sub" -> JsString(owner.subject.getOrElse("invalid")))
      payload.fields should (
        contain(
          "aud" -> JsArray(audience.map(aud => JsString(aud.id)))
        ) or contain(
          "aud" -> JsString(audience.headOption.map(_.id).getOrElse(""))
        )
      )
    }

    it should s"generate JWTs for resource owners without custom subject ($withKeyType)" in {
      val owner = Generators.generateResourceOwner.copy(subject = None)
      val audience = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateApi)
      val accessToken = generator.generate(owner, audience)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(accessToken.token.value)
      jws.setKey(withJwk.getKey)

      jws.verifySignature() should be(true)

      val payload = Json.parse(jws.getPayload).as[JsObject]
      payload.fields should contain("iss" -> JsString(issuer))
      payload.fields should contain("sub" -> JsString(owner.username))
      payload.fields should (
        contain(
          "aud" -> JsArray(audience.map(aud => JsString(aud.id)))
        ) or contain(
          "aud" -> JsString(audience.headOption.map(_.id).getOrElse(""))
        )
      )
    }
  }
}

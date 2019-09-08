package stasis.test.specs.unit.identity.model.tokens.generators

import org.jose4j.jwk.JsonWebKey
import org.jose4j.jws.JsonWebSignature
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import stasis.identity.model.tokens.generators.JwtBearerAccessTokenGenerator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

trait JwtBearerAccessTokenGeneratorBehaviour { _: AsyncUnitSpec =>
  def jwtBearerAccessTokenGenerator(withKeyType: String, withJwk: JsonWebKey): Unit = {
    val issuer = "some-issuer"
    val jwtExpiration = 3.seconds
    val generator = new JwtBearerAccessTokenGenerator(issuer, withJwk, jwtExpiration)

    it should s"generate JWTs for clients ($withKeyType)" in {
      val client = Generators.generateClient
      val audience = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateClient)
      val token = generator.generate(client, audience)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(token.value)
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

    it should s"generate JWTs for resource owners ($withKeyType)" in {
      val owner = Generators.generateResourceOwner
      val audience = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateApi)
      val token = generator.generate(owner, audience)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(token.value)
      jws.setKey(withJwk.getKey)

      jws.verifySignature() should be(true)

      val payload = Json.parse(jws.getPayload).as[JsObject]
      payload.fields should contain("iss" -> JsString(issuer))
      payload.fields should contain("sub" -> JsString(owner.username))
      payload.fields should (
        contain(
          "aud" -> JsArray(audience.map(aud => JsString(aud.id.toString)))
        ) or contain(
          "aud" -> JsString(audience.headOption.map(_.id.toString).getOrElse(""))
        )
      )
    }
  }
}

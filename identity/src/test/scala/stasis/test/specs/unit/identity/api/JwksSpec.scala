package stasis.test.specs.unit.identity.api

import akka.http.scaladsl.model.StatusCodes
import play.api.libs.json.{JsArray, JsObject, JsString}
import stasis.identity.api.Jwks
import stasis.test.specs.unit.core.security.jwt.mocks.MockJwksGenerators
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class JwksSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "JWKs routes" should "provide a list of available JSON web keys" in {
    val expectedKeys =
      Generators.generateSeq(
        min = 1,
        max = 3,
        g = MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(Generators.generateString(withSize = 16))
        )
      )

    val jwks = new Jwks(keys = expectedKeys)

    Get("/jwks.json") ~> jwks.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain(
        "keys" -> JsArray(expectedKeys.map(jwk => JsString(jwk.toJson)))
      )
    }
  }
}

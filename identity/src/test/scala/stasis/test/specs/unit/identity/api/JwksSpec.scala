package stasis.test.specs.unit.identity.api

import akka.http.scaladsl.model.StatusCodes
import play.api.libs.json.{JsArray, JsObject, Json}
import stasis.identity.api.Jwks
import stasis.test.specs.unit.core.security.mocks.MockJwksGenerators
import stasis.test.specs.unit.identity.RouteTest

class JwksSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "JWKs routes" should "provide a list of available JSON web keys" in {
    val expectedKeys =
      stasis.test.Generators.generateSeq(
        min = 1,
        max = 3,
        g = MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(stasis.test.Generators.generateString(withSize = 16))
        )
      )

    val jwks = new Jwks(keys = expectedKeys)

    Get("/jwks.json") ~> jwks.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain(
        "keys" -> JsArray(expectedKeys.map(jwk => Json.parse(jwk.toJson)))
      )
    }
  }
}

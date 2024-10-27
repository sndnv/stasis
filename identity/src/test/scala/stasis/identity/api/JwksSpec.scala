package stasis.identity.api

import org.apache.pekko.http.scaladsl.model.StatusCodes
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import stasis.identity.RouteTest
import stasis.layers
import stasis.layers.Generators
import stasis.layers.security.mocks.MockJwksGenerators

class JwksSpec extends RouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "JWKs routes" should "provide a list of available JSON web keys" in withRetry {
    val expectedKeys =
      Generators.generateSeq(
        min = 1,
        max = 3,
        g = MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(layers.Generators.generateString(withSize = 16))
        )
      )

    val jwks = new Jwks(keys = expectedKeys)

    Get("/jwks.json") ~> jwks.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain(
        "keys" -> Json.toJson(expectedKeys.map(jwk => Json.parse(jwk.toJson)))
      )
    }
  }
}

package stasis.test.specs.unit.identity.api.manage.requests

import scala.concurrent.duration._

import stasis.identity.api.manage.requests.UpdateClient
import stasis.layers.UnitSpec

class UpdateClientSpec extends UnitSpec {
  "An UpdateClient request" should "validate its content" in withRetry {
    val request = UpdateClient(tokenExpiration = 1.second, active = true)

    an[IllegalArgumentException] should be thrownBy request.copy(tokenExpiration = 0.seconds)
  }
}

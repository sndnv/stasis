package stasis.test.specs.unit.identity.api.manage.requests

import stasis.identity.api.manage.requests.UpdateClient
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class UpdateClientSpec extends UnitSpec {
  private val request = UpdateClient(
    tokenExpiration = 1.second,
    active = true
  )

  "An UpdateClient request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(tokenExpiration = 0.seconds)
  }
}

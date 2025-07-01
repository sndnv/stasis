package stasis.identity.api.manage.requests

import scala.concurrent.duration._

import io.github.sndnv.layers.testing.UnitSpec

class UpdateClientSpec extends UnitSpec {
  "An UpdateClient request" should "validate its content" in withRetry {
    val request = UpdateClient(tokenExpiration = 1.second, active = true)

    an[IllegalArgumentException] should be thrownBy request.copy(tokenExpiration = 0.seconds)
  }
}

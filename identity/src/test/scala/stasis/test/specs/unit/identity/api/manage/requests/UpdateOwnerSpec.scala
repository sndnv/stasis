package stasis.test.specs.unit.identity.api.manage.requests

import stasis.identity.api.manage.requests.UpdateOwner
import stasis.test.specs.unit.UnitSpec

class UpdateOwnerSpec extends UnitSpec {
  private val request = UpdateOwner(
    allowedScopes = Seq("some-scope"),
    active = true
  )

  "An UpdateOwner request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(allowedScopes = Seq.empty)
  }
}

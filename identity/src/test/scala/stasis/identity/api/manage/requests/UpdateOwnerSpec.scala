package stasis.identity.api.manage.requests

import io.github.sndnv.layers.testing.UnitSpec

class UpdateOwnerSpec extends UnitSpec {
  private val request = UpdateOwner(
    allowedScopes = Seq("some-scope"),
    active = true
  )

  "An UpdateOwner request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(allowedScopes = Seq.empty)
  }
}

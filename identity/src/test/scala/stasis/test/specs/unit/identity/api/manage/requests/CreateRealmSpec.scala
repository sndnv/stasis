package stasis.test.specs.unit.identity.api.manage.requests

import stasis.identity.api.manage.requests.CreateRealm
import stasis.identity.model.realms.Realm
import stasis.test.specs.unit.UnitSpec

class CreateRealmSpec extends UnitSpec {
  private val request = CreateRealm(
    id = "some-realm",
    refreshTokensAllowed = true
  )

  "A CreateRealm request" should "validate its content" in {
    an[IllegalArgumentException] should be thrownBy request.copy(id = "")
  }

  it should "be convertible to Realm" in {
    val expectedRealm = Realm(id = request.id, refreshTokensAllowed = request.refreshTokensAllowed)
    val actualRealm = request.toRealm

    actualRealm should be(expectedRealm)
  }
}

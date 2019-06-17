package stasis.test.specs.unit.identity.api.manage.requests

import stasis.identity.api.manage.requests.CreateApi
import stasis.identity.model.apis.Api
import stasis.test.specs.unit.UnitSpec

class CreateApiSpec extends UnitSpec {
  private val request = CreateApi(id = "some-api")

  "A CreateApi request" should "validate its content" in {
    an[IllegalArgumentException] should be thrownBy request.copy(id = "")
  }

  it should "be convertible to Api" in {
    val expectedApi = Api(id = request.id, realm = "some-realm")
    val actualApi = request.toApi(realm = expectedApi.realm)

    actualApi should be(expectedApi)
  }
}

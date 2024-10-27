package stasis.identity.api.manage.requests

import stasis.identity.model.apis.Api
import stasis.layers.UnitSpec

class CreateApiSpec extends UnitSpec {
  private val request = CreateApi(id = "some-api")

  "A CreateApi request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(id = "")
  }

  it should "be convertible to Api" in withRetry {
    val expectedApi = Api.create(id = request.id)
    val actualApi = request.toApi.copy(created = expectedApi.created, updated = expectedApi.updated)

    actualApi should be(expectedApi)
  }
}

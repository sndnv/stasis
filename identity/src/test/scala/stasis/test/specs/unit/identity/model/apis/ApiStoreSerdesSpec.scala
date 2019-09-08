package stasis.test.specs.unit.identity.model.apis

import stasis.identity.model.apis.ApiStoreSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class ApiStoreSerdesSpec extends UnitSpec {
  "ApiStoreSerdes" should "serialize and deserialize keys" in {
    val apiId = stasis.test.Generators.generateString(withSize = 32)

    val serialized = ApiStoreSerdes.serializeKey(apiId)
    val deserialized = ApiStoreSerdes.deserializeKey(serialized)

    deserialized should be(apiId)
  }

  they should "serialize and deserialize values" in {
    val api = Generators.generateApi

    val serialized = ApiStoreSerdes.serializeValue(api)
    val deserialized = ApiStoreSerdes.deserializeValue(serialized)

    deserialized should be(api)
  }
}

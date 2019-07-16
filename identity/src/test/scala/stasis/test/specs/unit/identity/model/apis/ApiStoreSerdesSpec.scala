package stasis.test.specs.unit.identity.model.apis

import stasis.identity.model.apis.ApiStoreSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class ApiStoreSerdesSpec extends UnitSpec {
  "ApiStoreSerdes" should "serialize and deserialize keys" in {
    val apiId = Generators.generateString(withSize = 32)
    val realmId = Generators.generateRealmId

    val serialized = ApiStoreSerdes.serializeKey((realmId, apiId))
    val deserialized = ApiStoreSerdes.deserializeKey(serialized)

    deserialized should be((realmId, apiId))
  }

  they should "fail to deserialize invalid keys" in {
    an[IllegalArgumentException] should be thrownBy ApiStoreSerdes.deserializeKey("invalid-id")
  }

  they should "serialize and deserialize values" in {
    val api = Generators.generateApi

    val serialized = ApiStoreSerdes.serializeValue(api)
    val deserialized = ApiStoreSerdes.deserializeValue(serialized)

    deserialized should be(api)
  }
}

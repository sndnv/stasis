package stasis.test.specs.unit.identity.model.owners

import stasis.identity.model.owners.ResourceOwnerStoreSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class ResourceOwnerStoreSerdesSpec extends UnitSpec {
  "ResourceOwnerStoreSerdes" should "serialize and deserialize keys" in {
    val owner = Generators.generateString(withSize = 32)

    val serialized = ResourceOwnerStoreSerdes.serializeKey(owner)
    val deserialized = ResourceOwnerStoreSerdes.deserializeKey(serialized)

    deserialized should be(owner)
  }

  they should "serialize and deserialize values" in {
    val owner = Generators.generateResourceOwner

    val serialized = ResourceOwnerStoreSerdes.serializeValue(owner)
    val deserialized = ResourceOwnerStoreSerdes.deserializeValue(serialized)

    deserialized should be(owner)
  }
}

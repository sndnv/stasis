package stasis.test.specs.unit.identity.model.realms

import stasis.identity.model.realms.RealmStoreSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class RealmStoreSerdesSpec extends UnitSpec {
  "RealmStoreSerdes" should "serialize and deserialize keys" in {
    val realm = Generators.generateRealmId

    val serialized = RealmStoreSerdes.serializeKey(realm)
    val deserialized = RealmStoreSerdes.deserializeKey(serialized)

    deserialized should be(realm)
  }

  they should "serialize and deserialize values" in {
    val realm = Generators.generateRealm

    val serialized = RealmStoreSerdes.serializeValue(realm)
    val deserialized = RealmStoreSerdes.deserializeValue(serialized)

    deserialized should be(realm)
  }
}

package stasis.test.specs.unit.identity.model.realms

import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class RealmSpec extends UnitSpec {
  "A Realm" should "validate its identifier" in {
    val realm = Generators.generateRealm
    an[IllegalArgumentException] should be thrownBy realm.copy(id = "")
    an[IllegalArgumentException] should be thrownBy realm.copy(id = "abc~def")
  }
}

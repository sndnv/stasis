package stasis.test.specs.unit.server.model.users

import stasis.server.model.users.UserStoreSerdes
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.server.model.Generators

class UserStoreSerdesSpec extends UnitSpec {
  "UserStoreSerdes" should "serialize and deserialize keys" in {
    val user = User.generateId()

    val serialized = UserStoreSerdes.serializeKey(user)
    val deserialized = UserStoreSerdes.deserializeKey(serialized)

    deserialized should be(user)
  }

  they should "serialize and deserialize values" in {
    val user = Generators.generateUser

    val serialized = UserStoreSerdes.serializeValue(user)
    val deserialized = UserStoreSerdes.deserializeValue(serialized)

    deserialized should be(user)
  }
}

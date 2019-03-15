package stasis.test.specs.unit.server.api.requests

import stasis.server.api.requests.CreateUser
import stasis.server.model.users.User
import stasis.test.specs.unit.UnitSpec

class CreateUserSpec extends UnitSpec {
  it should "convert requests to users" in {
    val expectedUser = User(
      id = User.generateId(),
      isActive = true,
      limits = None,
      permissions = Set.empty
    )

    val request = CreateUser(
      limits = expectedUser.limits,
      permissions = expectedUser.permissions
    )

    request.toUser.copy(id = expectedUser.id) should be(expectedUser)
  }
}

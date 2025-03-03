package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import stasis.shared.api.requests.CreateUser
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class CreateUserSpec extends UnitSpec {
  it should "convert requests to users" in {
    val salt = "test-salt"

    val expectedUser = User(
      id = User.generateId(),
      salt = salt,
      active = true,
      limits = None,
      permissions = Set.empty,
      created = Instant.now(),
      updated = Instant.now()
    )

    val request = CreateUser(
      username = "test-user",
      rawPassword = "test-password",
      limits = expectedUser.limits,
      permissions = expectedUser.permissions
    )

    request
      .toUser(withSalt = salt)
      .copy(id = expectedUser.id, created = expectedUser.created, updated = expectedUser.updated) should be(expectedUser)
  }
}

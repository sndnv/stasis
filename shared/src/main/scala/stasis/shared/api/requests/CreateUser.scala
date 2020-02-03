package stasis.shared.api.requests

import stasis.shared.model.users.User
import stasis.shared.security.Permission

final case class CreateUser(
  limits: Option[User.Limits],
  permissions: Set[Permission]
)

object CreateUser {
  implicit class RequestToUser(request: CreateUser) {
    def toUser(withSalt: String): User =
      User(
        id = User.generateId(),
        salt = withSalt,
        active = true,
        limits = request.limits,
        permissions = request.permissions
      )
  }
}

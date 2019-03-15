package stasis.server.api.requests

import stasis.server.model.users.User
import stasis.server.security.Permission

final case class CreateUser(
  limits: Option[User.Limits],
  permissions: Set[Permission]
)

object CreateUser {
  implicit class RequestToUser(request: CreateUser) {
    def toUser: User =
      User(
        id = User.generateId(),
        isActive = true,
        limits = request.limits,
        permissions = request.permissions
      )
  }
}

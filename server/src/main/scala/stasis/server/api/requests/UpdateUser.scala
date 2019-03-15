package stasis.server.api.requests

import stasis.server.model.users.User

private[api] trait UpdateUser

object UpdateUser {
  implicit class RequestToUpdatedUser(request: UpdateUser) {
    def toUpdatedUser(user: User): User =
      request match {
        case UpdateUserState(isActive)          => user.copy(isActive = isActive)
        case UpdateUserLimits(limits)           => user.copy(limits = limits)
        case UpdateUserPermissions(permissions) => user.copy(permissions = permissions)
      }
  }
}

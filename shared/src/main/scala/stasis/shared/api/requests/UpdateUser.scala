package stasis.shared.api.requests

import stasis.shared.model.users.User

private[stasis] trait UpdateUser

object UpdateUser {
  implicit class RequestToUpdatedUser(request: UpdateUser) {
    def toUpdatedUser(user: User): User =
      request match {
        case UpdateUserState(active)            => user.copy(active = active)
        case UpdateUserLimits(limits)           => user.copy(limits = limits)
        case UpdateUserPermissions(permissions) => user.copy(permissions = permissions)
      }
  }
}

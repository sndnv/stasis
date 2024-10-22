package stasis.shared.api.requests

import java.time.Instant

import stasis.shared.model.users.User

private[stasis] trait UpdateUser

object UpdateUser {
  implicit class RequestToUpdatedUser(request: UpdateUser) {
    def toUpdatedUser(user: User): User =
      request match {
        case UpdateUserState(active)            => user.copy(active = active, updated = Instant.now())
        case UpdateUserLimits(limits)           => user.copy(limits = limits, updated = Instant.now())
        case UpdateUserPermissions(permissions) => user.copy(permissions = permissions, updated = Instant.now())
        case UpdateUserSalt(salt)               => user.copy(salt = salt, updated = Instant.now())
      }
  }
}

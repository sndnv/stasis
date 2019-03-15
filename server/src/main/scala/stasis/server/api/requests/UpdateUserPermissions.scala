package stasis.server.api.requests
import stasis.server.security.Permission

final case class UpdateUserPermissions(permissions: Set[Permission]) extends UpdateUser

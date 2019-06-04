package stasis.shared.api.requests

import stasis.shared.security.Permission

final case class UpdateUserPermissions(permissions: Set[Permission]) extends UpdateUser

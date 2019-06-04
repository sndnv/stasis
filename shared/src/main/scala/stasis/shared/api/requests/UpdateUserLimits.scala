package stasis.shared.api.requests

import stasis.shared.model.users.User

final case class UpdateUserLimits(limits: Option[User.Limits]) extends UpdateUser

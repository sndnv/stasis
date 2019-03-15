package stasis.server.api.requests
import stasis.server.model.users.User

final case class UpdateUserLimits(limits: Option[User.Limits]) extends UpdateUser

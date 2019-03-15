package stasis.server.api.responses
import stasis.server.model.users.User

final case class CreatedUser(user: User.Id)

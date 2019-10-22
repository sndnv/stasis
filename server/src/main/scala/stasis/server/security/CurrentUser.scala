package stasis.server.security
import stasis.shared.model.users.User

final case class CurrentUser(id: User.Id) {
  override def toString: String = id.toString
}

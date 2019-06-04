package stasis.client.encryption.secrets

import akka.util.ByteString
import stasis.shared.model.users.User

// doc - sent to auth provider
final case class UserHashedAuthenticationPassword(
  user: User.Id,
  private val hashedPassword: ByteString
) extends Secret

object UserHashedAuthenticationPassword {
  def apply(user: User.Id, hashedPassword: ByteString): UserHashedAuthenticationPassword =
    new UserHashedAuthenticationPassword(user, hashedPassword)
}

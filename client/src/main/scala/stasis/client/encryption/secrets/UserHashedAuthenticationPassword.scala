package stasis.client.encryption.secrets

import java.util.concurrent.atomic.AtomicBoolean

import akka.util.ByteString
import stasis.shared.model.users.User

// doc - sent to auth provider
final case class UserHashedAuthenticationPassword(
  user: User.Id,
  private val hashedPassword: ByteString
) extends Secret {
  private val extracted: AtomicBoolean = new AtomicBoolean(false)

  def extract(): String = {
    val alreadyExtracted = extracted.getAndSet(true)

    if (alreadyExtracted) {
      throw new IllegalStateException("Password already extracted")
    } else {
      hashedPassword.utf8String
    }
  }
}

object UserHashedAuthenticationPassword {
  def apply(user: User.Id, hashedPassword: ByteString): UserHashedAuthenticationPassword =
    new UserHashedAuthenticationPassword(user, hashedPassword)
}

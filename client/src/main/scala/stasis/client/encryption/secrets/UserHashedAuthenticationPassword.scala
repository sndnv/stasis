package stasis.client.encryption.secrets

import akka.util.ByteString
import stasis.shared.model.users.User
import stasis.shared.secrets.DerivedPasswords

import java.util.concurrent.atomic.AtomicBoolean

// doc - sent to auth provider
final case class UserHashedAuthenticationPassword(
  user: User.Id,
  private val hashedPassword: ByteString
) extends Secret {
  private val extracted: AtomicBoolean = new AtomicBoolean(false)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def extract(): String = {
    val alreadyExtracted = extracted.getAndSet(true)

    if (alreadyExtracted) {
      throw new IllegalStateException("Password already extracted")
    } else {
      DerivedPasswords.encode(hashedPassword)
    }
  }
}

object UserHashedAuthenticationPassword {
  def apply(user: User.Id, hashedPassword: ByteString): UserHashedAuthenticationPassword =
    new UserHashedAuthenticationPassword(user, hashedPassword)
}

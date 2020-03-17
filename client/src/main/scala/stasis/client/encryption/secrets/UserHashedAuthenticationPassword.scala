package stasis.client.encryption.secrets

import java.util.concurrent.atomic.AtomicBoolean

import java.util.Base64
import akka.util.ByteString
import stasis.shared.model.users.User

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
      Base64.getUrlEncoder.withoutPadding.encodeToString(hashedPassword.toArray)
    }
  }
}

object UserHashedAuthenticationPassword {
  def apply(user: User.Id, hashedPassword: ByteString): UserHashedAuthenticationPassword =
    new UserHashedAuthenticationPassword(user, hashedPassword)
}

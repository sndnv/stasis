package stasis.client.encryption.secrets

import org.apache.pekko.util.ByteString
import stasis.shared.model.users.User
import stasis.shared.secrets.DerivedPasswords

import java.util.concurrent.atomic.AtomicBoolean

// doc - sent to auth provider
sealed trait UserAuthenticationPassword extends Secret {
  def extract(): String
}

object UserAuthenticationPassword {
  final case class Hashed(
    user: User.Id,
    private val hashedPassword: ByteString
  ) extends UserAuthenticationPassword {
    private val extracted: AtomicBoolean = new AtomicBoolean(false)

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def extract(): String = {
      val alreadyExtracted = extracted.getAndSet(true)

      if (alreadyExtracted) {
        throw new IllegalStateException("Password already extracted")
      } else {
        DerivedPasswords.encode(hashedPassword)
      }
    }
  }

  object Hashed {
    def apply(user: User.Id, hashedPassword: ByteString): Hashed =
      new Hashed(user, hashedPassword)
  }

  final case class Unhashed(
    user: User.Id,
    private val rawPassword: ByteString
  ) extends UserAuthenticationPassword {
    private val extracted: AtomicBoolean = new AtomicBoolean(false)

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def extract(): String = {
      val alreadyExtracted = extracted.getAndSet(true)

      if (alreadyExtracted) {
        throw new IllegalStateException("Password already extracted")
      } else {
        rawPassword.utf8String
      }
    }
  }

  object Unhashed {
    def apply(user: User.Id, rawPassword: ByteString): Unhashed =
      new Unhashed(user, rawPassword)
  }
}

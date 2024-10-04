package stasis.client.encryption.secrets

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.pekko.util.ByteString

import stasis.shared.model.users.User
import stasis.shared.secrets.DerivedPasswords

// doc - sent to auth provider
sealed trait UserAuthenticationPassword extends Secret {
  def extract(): String
  def digested(): String
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

    override def digested(): String = DerivedPasswords.digest(password = hashedPassword)
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

    override def digested(): String = DerivedPasswords.digest(password = rawPassword)
  }

  object Unhashed {
    def apply(user: User.Id, rawPassword: ByteString): Unhashed =
      new Unhashed(user, rawPassword)
  }
}

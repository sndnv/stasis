package stasis.client_android.lib.encryption.secrets

import okio.ByteString
import stasis.client_android.lib.model.server.users.UserId
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

sealed class UserAuthenticationPassword : Secret() {
    abstract val user: UserId
    abstract fun extract(): String

   data class Hashed(
       override val user: UserId,
       private val hashedPassword: ByteString
    ) : UserAuthenticationPassword() {
        private val extracted: AtomicBoolean = AtomicBoolean(false)

        override fun extract(): String {
            val alreadyExtracted = extracted.getAndSet(true)

            return if (alreadyExtracted) {
                throw IllegalStateException("Password already extracted")
            } else {
                Base64.getUrlEncoder().withoutPadding().encodeToString(hashedPassword.toByteArray())
            }
        }
    }

    data class Unhashed(
        override val user: UserId,
        private val rawPassword: ByteString
    ) : UserAuthenticationPassword() {
        private val extracted: AtomicBoolean = AtomicBoolean(false)

        override fun extract(): String {
            val alreadyExtracted = extracted.getAndSet(true)

            return if (alreadyExtracted) {
                throw IllegalStateException("Password already extracted")
            } else {
               rawPassword.utf8()
            }
        }
    }
}

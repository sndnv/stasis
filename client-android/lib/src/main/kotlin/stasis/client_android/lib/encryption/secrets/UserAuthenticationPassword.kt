package stasis.client_android.lib.encryption.secrets

import okio.ByteString
import stasis.client_android.lib.model.server.users.UserId
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

sealed class UserAuthenticationPassword : Secret() {
    abstract val user: UserId
    abstract fun extract(): String
    abstract fun digested(): String

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

        override fun digested(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-512").digest(hashedPassword.toByteArray())
            )
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

        override fun digested(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-512").digest(rawPassword.toByteArray())
            )
    }
}

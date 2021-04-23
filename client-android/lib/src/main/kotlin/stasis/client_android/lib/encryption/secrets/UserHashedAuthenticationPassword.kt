package stasis.client_android.lib.encryption.secrets

import okio.ByteString
import stasis.client_android.lib.model.server.users.UserId
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

data class UserHashedAuthenticationPassword(
    val user: UserId,
    private val hashedPassword: ByteString
) : Secret() {
    private val extracted: AtomicBoolean = AtomicBoolean(false)

    fun extract(): String {
        val alreadyExtracted = extracted.getAndSet(true)

        return if (alreadyExtracted) {
            throw IllegalStateException("Password already extracted")
        } else {
            Base64.getUrlEncoder().withoutPadding().encodeToString(hashedPassword.toByteArray())
        }
    }
}

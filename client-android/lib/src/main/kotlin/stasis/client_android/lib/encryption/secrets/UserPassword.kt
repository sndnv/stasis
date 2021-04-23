package stasis.client_android.lib.encryption.secrets

import okio.ByteString
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.server.users.UserId
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class UserPassword(
    val user: UserId,
    val salt: String,
    private val password: CharArray,
    val target: Config
) : Secret() {
    fun toHashedAuthenticationPassword(): UserHashedAuthenticationPassword =
        UserHashedAuthenticationPassword(
            user = user,
            hashedPassword = derivePassword(
                password = password,
                salt = "${target.derivation.authentication.saltPrefix}-authentication-$salt",
                iterations = target.derivation.authentication.iterations,
                derivedKeySize = target.derivation.authentication.secretSize
            )
        )

    fun toHashedEncryptionPassword(): UserHashedEncryptionPassword =
        UserHashedEncryptionPassword(
            user = user,
            hashedPassword = derivePassword(
                password = password,
                salt = "${target.derivation.encryption.saltPrefix}-encryption-$salt",
                iterations = target.derivation.encryption.iterations,
                derivedKeySize = target.derivation.encryption.secretSize
            ),
            target = target
        )

    companion object {
        object Defaults {
            val Charset: Charset = StandardCharsets.UTF_8
            const val Algorithm: String = "PBKDF2WithHmacSHA512"
        }

        private const val Bytes: Int = 8

        fun derivePassword(
            password: CharArray,
            salt: String,
            iterations: Int,
            derivedKeySize: Int
        ): ByteString {
            val spec = PBEKeySpec(
                password,
                salt.toByteArray(Defaults.Charset),
                iterations,
                derivedKeySize * Bytes
            )

            val derivedPassword = SecretKeyFactory
                .getInstance(Defaults.Algorithm)
                .generateSecret(spec)
                .encoded

            return derivedPassword.toByteString()
        }
    }
}

package stasis.client_android.lib.encryption.secrets

import java.nio.ByteBuffer
import java.util.UUID

abstract class Secret {
    final override fun toString(): String = "Secret(${this.javaClass.name})"

    private fun UUID.toByteBuffer(): ByteBuffer =
        ByteBuffer
            .allocate(UUID_SIZE)
            .putLong(mostSignificantBits)
            .putLong(leastSignificantBits)

    fun UUID.toBytes(): ByteArray = toByteBuffer().array()

    companion object {
        val UUID_SIZE: Int =
            listOf(
                java.lang.Long.BYTES, // most significant bits
                java.lang.Long.BYTES // least significant bits
            ).sum()
    }

    data class EncryptionSecretConfig(
        val keySize: Int,
        val ivSize: Int
    ) {
        init {
            require(keySize >= MinKeySize) { "key must not be smaller than 16 bytes" }
            require(ivSize >= MinIvSize) { "iv must not be smaller than 12 bytes" }
        }

        companion object {
            const val MinKeySize: Int = 16
            const val MinIvSize: Int = 12
        }
    }

    data class EncryptionKeyDerivationConfig(
        val secretSize: Int,
        val iterations: Int,
        val saltPrefix: String
    ) {
        init {
            require(secretSize >= MinSecretSize) { "secret must not be smaller than 16 bytes" }
            require(iterations >= MinIterations) { "iterations must not be fewer than 100k" }
        }

        companion object {
            const val MinSecretSize: Int = 16
            const val MinIterations: Int = 100000
        }
    }

    data class AuthenticationKeyDerivationConfig(
        val enabled: Boolean,
        val secretSize: Int,
        val iterations: Int,
        val saltPrefix: String
    ) {
        init {
            require(secretSize >= MinSecretSize) { "secret must not be smaller than 16 bytes" }
            require(iterations >= MinIterations) { "iterations must not be fewer than 100k" }
        }

        companion object {
            const val MinSecretSize: Int = 16
            const val MinIterations: Int = 100000
        }
    }

    data class Config(
        val derivation: DerivationConfig,
        val encryption: EncryptionConfig
    ) {
        data class DerivationConfig(
            val encryption: EncryptionKeyDerivationConfig,
            val authentication: AuthenticationKeyDerivationConfig
        )

        data class EncryptionConfig(
            val file: EncryptionSecretConfig,
            val metadata: EncryptionSecretConfig,
            val deviceSecret: EncryptionSecretConfig
        )
    }
}

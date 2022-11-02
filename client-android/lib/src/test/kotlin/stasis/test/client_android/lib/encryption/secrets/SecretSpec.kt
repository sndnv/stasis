package stasis.test.client_android.lib.encryption.secrets

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec

class SecretSpec : WordSpec({
    "Secrets" should {
        "validate their config" {
            val authenticationDerivationConfig = SecretsConfig.testConfig.derivation.authentication
            shouldThrow<IllegalArgumentException> { authenticationDerivationConfig.copy(secretSize = 8) }
            shouldThrow<IllegalArgumentException> { authenticationDerivationConfig.copy(iterations = 10000) }

            val encryptionDerivationConfig = SecretsConfig.testConfig.derivation.encryption
            shouldThrow<IllegalArgumentException> { encryptionDerivationConfig.copy(secretSize = 8) }
            shouldThrow<IllegalArgumentException> { encryptionDerivationConfig.copy(iterations = 10000) }

            val encryptionConfig = SecretsConfig.testConfig.encryption.deviceSecret
            shouldThrow<IllegalArgumentException> { encryptionConfig.copy(keySize = 8) }
            shouldThrow<IllegalArgumentException> { encryptionConfig.copy(ivSize = 8) }
        }
    }
})

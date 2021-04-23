package stasis.test.client_android.lib.encryption.secrets

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec

class SecretSpec : WordSpec({
    "Secrets" should {
        "validate their config" {
            val derivationConfig = SecretsConfig.testConfig.derivation.authentication
            shouldThrow<IllegalArgumentException> { derivationConfig.copy(secretSize = 8) }
            shouldThrow<IllegalArgumentException> { derivationConfig.copy(iterations = 10000) }

            val encryptionConfig = SecretsConfig.testConfig.encryption.deviceSecret
            shouldThrow<IllegalArgumentException> { encryptionConfig.copy(keySize = 8) }
            shouldThrow<IllegalArgumentException> { encryptionConfig.copy(ivSize = 8) }
        }
    }
})

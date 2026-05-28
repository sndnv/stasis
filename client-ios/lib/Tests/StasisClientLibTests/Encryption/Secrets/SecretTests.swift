import Foundation
@testable import StasisClientLib
import Testing

@Suite("Secret")
struct SecretTests {
    @Test("validates authentication-derivation config")
    func validatesAuthenticationDerivationConfig() {
        let original = SecretsConfigFixtures.testConfig.derivation.authentication
        #expect(throws: SecretError.self) {
            _ = try AuthenticationKeyDerivationConfig(
                enabled: original.enabled, secretSize: 8,
                iterations: original.iterations, saltPrefix: original.saltPrefix
            )
        }
        #expect(throws: SecretError.self) {
            _ = try AuthenticationKeyDerivationConfig(
                enabled: original.enabled, secretSize: original.secretSize,
                iterations: 10000, saltPrefix: original.saltPrefix
            )
        }
    }

    @Test("validates encryption-derivation config")
    func validatesEncryptionDerivationConfig() {
        let original = SecretsConfigFixtures.testConfig.derivation.encryption
        #expect(throws: SecretError.self) {
            _ = try EncryptionKeyDerivationConfig(
                secretSize: 8, iterations: original.iterations, saltPrefix: original.saltPrefix
            )
        }
        #expect(throws: SecretError.self) {
            _ = try EncryptionKeyDerivationConfig(
                secretSize: original.secretSize, iterations: 10000, saltPrefix: original.saltPrefix
            )
        }
    }

    @Test("validates encryption secret config")
    func validatesEncryptionSecretConfig() {
        let original = SecretsConfigFixtures.testConfig.encryption.deviceSecret
        #expect(throws: SecretError.self) {
            _ = try EncryptionSecretConfig(keySize: 8, ivSize: original.ivSize)
        }
        #expect(throws: SecretError.self) {
            _ = try EncryptionSecretConfig(keySize: original.keySize, ivSize: 8)
        }
    }
}

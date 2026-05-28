import Foundation
@testable import StasisClientLib

enum SecretsConfigFixtures {
    static let testUser: UserId = UUID(uuidString: "731404fe-4e67-4767-b8d7-2814f126a5c8")!
    static let testDevice: DeviceId = UUID(uuidString: "434b9696-824c-4927-ba8e-dda5704084f8")!

    static let testConfig: SecretConfig = {
        do {
            return SecretConfig(
                derivation: SecretConfig.DerivationConfig(
                    encryption: try EncryptionKeyDerivationConfig(
                        secretSize: 64, iterations: 100000, saltPrefix: "unit-test"
                    ),
                    authentication: try AuthenticationKeyDerivationConfig(
                        enabled: true, secretSize: 64, iterations: 100000, saltPrefix: "unit-test"
                    )
                ),
                encryption: SecretConfig.EncryptionConfig(
                    file: try EncryptionSecretConfig(keySize: 16, ivSize: 16),
                    metadata: try EncryptionSecretConfig(keySize: 24, ivSize: 32),
                    deviceSecret: try EncryptionSecretConfig(keySize: 32, ivSize: 64)
                )
            )
        } catch {
            fatalError("Failed to build test secrets config: \(error)")
        }
    }()
}

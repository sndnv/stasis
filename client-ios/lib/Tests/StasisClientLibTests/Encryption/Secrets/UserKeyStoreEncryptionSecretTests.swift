import Foundation
@testable import StasisClientLib
import Testing

@Suite("UserKeyStoreEncryptionSecret")
struct UserKeyStoreEncryptionSecretTests {
    private let encryptionIv = Data(base64Encoded:
        "6mSjkDoXUNNK8TGbebRYWXnjfskeVHXhaMxBRKD+ITvMckUp0ZQtUeEttz9pA0vWQ4MKa8otGmyDJ7OCrdeY4g=="
    )!
    private let encryptionKey = Data(base64Encoded: "kROAgx70MxRKeDODRMyshRH0tswcd4jydKA60r+5knI=")!
    private let secret = Data(base64Encoded: "BOunLSLKxVbluhDSPZ/wWw==")!

    private var keyStoreSecret: UserKeyStoreEncryptionSecret {
        UserKeyStoreEncryptionSecret(
            user: SecretsConfigFixtures.testUser, iv: encryptionIv, key: encryptionKey,
            target: SecretsConfigFixtures.testConfig
        )
    }

    private var deviceSecret: DeviceSecret {
        DeviceSecret(
            user: SecretsConfigFixtures.testUser, device: SecretsConfigFixtures.testDevice,
            secret: secret, target: SecretsConfigFixtures.testConfig
        )
    }

    private let encryptedDeviceSecret = Data(base64Encoded: "lEc6SelbtpNPtzcm6AjkXQR51FNeWuM0xkWSXKARiFQ=")!

    @Test("encrypts device secrets")
    func encryptsDeviceSecret() throws {
        let actual = try keyStoreSecret.encryptDeviceSecret(deviceSecret)
        #expect(actual == encryptedDeviceSecret)
    }

    @Test("decrypts device secrets")
    func decryptsDeviceSecret() throws {
        let actual = try keyStoreSecret.decryptDeviceSecret(
            device: SecretsConfigFixtures.testDevice, encryptedSecret: encryptedDeviceSecret
        )
        #expect(actual == deviceSecret)
    }

    @Test("does not render its content via description")
    func description() {
        #expect(keyStoreSecret.description == "Secret(\(String(reflecting: type(of: keyStoreSecret))))")
    }
}

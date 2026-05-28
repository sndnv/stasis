import Foundation
@testable import StasisClientLib
import Testing

@Suite("UserLocalEncryptionSecret")
struct UserLocalEncryptionSecretTests {
    private let encryptionIv = Data(base64Encoded:
        "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
    )!
    private let encryptionKey = Data(base64Encoded: "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU=")!
    private let secret = Data(base64Encoded: "BOunLSLKxVbluhDSPZ/wWw==")!

    private var localSecret: UserLocalEncryptionSecret {
        UserLocalEncryptionSecret(
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

    private let encryptedDeviceSecret = Data(base64Encoded: "z+pus9Glc/HssFVWozd+T2iuw4OOM4aeqcdDY+gvG8M=")!

    @Test("encrypts device secrets")
    func encryptsDeviceSecret() throws {
        let actual = try localSecret.encryptDeviceSecret(deviceSecret)
        #expect(actual == encryptedDeviceSecret)
    }

    @Test("decrypts device secrets")
    func decryptsDeviceSecret() throws {
        let actual = try localSecret.decryptDeviceSecret(
            device: SecretsConfigFixtures.testDevice, encryptedSecret: encryptedDeviceSecret
        )
        #expect(actual == deviceSecret)
    }

    @Test("does not render its content via description")
    func description() {
        #expect(localSecret.description == "Secret(\(String(reflecting: type(of: localSecret))))")
    }
}

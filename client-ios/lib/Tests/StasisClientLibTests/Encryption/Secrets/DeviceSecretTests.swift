import Foundation
@testable import StasisClientLib
import Testing

@Suite("DeviceSecret")
struct DeviceSecretTests {
    private let encryptionIv = Data(base64Encoded:
        "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
    )!
    private let encryptionKey = Data(base64Encoded: "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU=")!
    private let secret = Data(base64Encoded: "BOunLSLKxVbluhDSPZ/wWw==")!

    private var deviceSecret: DeviceSecret {
        DeviceSecret(
            user: SecretsConfigFixtures.testUser, device: SecretsConfigFixtures.testDevice,
            secret: secret, target: SecretsConfigFixtures.testConfig
        )
    }

    private let encryptedDeviceSecret = Data(base64Encoded: "z+pus9Glc/HssFVWozd+T2iuw4OOM4aeqcdDY+gvG8M=")!

    @Test("supports encryption")
    func supportsEncryption() throws {
        let actual = try deviceSecret.encrypted { plaintext in
            try Aes.encrypt(plaintext: plaintext, key: encryptionKey, iv: encryptionIv)
        }
        #expect(actual == encryptedDeviceSecret)
    }

    @Test("supports decryption")
    func supportsDecryption() throws {
        let actual = try DeviceSecret.decrypted(
            user: SecretsConfigFixtures.testUser,
            device: SecretsConfigFixtures.testDevice,
            encryptedSecret: encryptedDeviceSecret,
            decryptionStage: { ciphertext in
                try Aes.decrypt(ciphertext: ciphertext, key: encryptionKey, iv: encryptionIv)
            },
            target: SecretsConfigFixtures.testConfig
        )
        #expect(actual == deviceSecret)
    }

    @Test("generates file secrets")
    func generatesFileSecret() {
        let file = "/tmp/some/file"
        let iv = Data(base64Encoded: "uXE+Ru1aojwZa+8IVE49mg==")!
        let key = Data(base64Encoded: "aHhX4zqPGYLnr+WI9RF23Q==")!

        let actual = deviceSecret.toFileSecret(forFile: file, checksum: Data([0x2A]))
        #expect(actual == DeviceFileSecret(file: file, iv: iv, key: key))
    }

    @Test("generates metadata secrets")
    func generatesMetadataSecret() {
        let metadata: CrateId = UUID(uuidString: "2b94caba-7c28-4322-9d72-fc8e72f884d5")!
        let iv = Data(base64Encoded: "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM=")!
        let key = Data(base64Encoded: "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w")!

        let actual = deviceSecret.toMetadataSecret(metadataCrate: metadata)
        #expect(actual == DeviceMetadataSecret(iv: iv, key: key))
    }

    @Test("does not render its content via description")
    func description() {
        #expect(deviceSecret.description == "Secret(\(String(reflecting: type(of: deviceSecret))))")
    }
}

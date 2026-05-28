import Foundation
@testable import StasisClientLib
import Testing

@Suite("DeviceMetadataSecret")
struct DeviceMetadataSecretTests {
    private let encryptionIv = Data(base64Encoded: "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM=")!
    private let encryptionKey = Data(base64Encoded: "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w")!

    private let plaintextData = Data("some-plaintext-data".utf8)
    private let encryptedData = Data(base64Encoded: "coKEpIHZVHZtPcYuojvMPcOD8S6a2sH4Xg0gPX8UTKz75Ts=")!

    private var metadataSecret: DeviceMetadataSecret {
        DeviceMetadataSecret(iv: encryptionIv, key: encryptionKey)
    }

    @Test("supports encryption")
    func supportsEncryption() throws {
        let actual = try metadataSecret.encrypt(plaintextData)
        #expect(actual == encryptedData)
    }

    @Test("supports decryption")
    func supportsDecryption() throws {
        let actual = try metadataSecret.decrypt(encryptedData)
        #expect(actual == plaintextData)
    }

    @Test("does not render its content via description")
    func description() {
        #expect(metadataSecret.description == "Secret(\(String(reflecting: type(of: metadataSecret))))")
    }
}

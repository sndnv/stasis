import Foundation
@testable import StasisClientLib
import Testing

@Suite("Encrypting / Decrypting")
struct EncryptingDecryptingTests {
    private let encryptionIv = Data(base64Encoded: "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM=")!
    private let encryptionKey = Data(base64Encoded: "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w")!

    private var fileSecret: DeviceFileSecret {
        DeviceFileSecret(file: "/tmp/some/file", iv: encryptionIv, key: encryptionKey)
    }

    private var metadataSecret: DeviceMetadataSecret {
        DeviceMetadataSecret(iv: encryptionIv, key: encryptionKey)
    }

    @Test("Encrypting.maxPlaintextSize reports the 4 GB GCM limit")
    func reportsMaxPlaintextSize() {
        let encoder: any Encrypting = Aes.shared
        #expect(encoder.maxPlaintextSize == 4 * 1024 * 1024 * 1024)
    }

    @Test("round-trips through the Encrypting/Decrypting surface with a file secret")
    func fileSecretRoundTripViaProtocol() throws {
        let encoder: any Encrypting = Aes.shared
        let decoder: any Decrypting = Aes.shared
        let plaintext = Data("payload-via-protocol".utf8)
        let ciphertext = try encoder.encrypt(plaintext, fileSecret: fileSecret)
        #expect(ciphertext != plaintext)
        let decrypted = try decoder.decrypt(ciphertext, fileSecret: fileSecret)
        #expect(decrypted == plaintext)
    }

    @Test("round-trips through the Encrypting/Decrypting surface with a metadata secret")
    func metadataSecretRoundTripViaProtocol() throws {
        let encoder: any Encrypting = Aes.shared
        let decoder: any Decrypting = Aes.shared
        let plaintext = Data("metadata-via-protocol".utf8)
        let ciphertext = try encoder.encrypt(plaintext, metadataSecret: metadataSecret)
        let decrypted = try decoder.decrypt(ciphertext, metadataSecret: metadataSecret)
        #expect(decrypted == plaintext)
    }
}

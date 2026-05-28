import Foundation
@testable import StasisClientLib
import Testing

@Suite("Aes")
struct AesTests {
    private let encryptionIv = Data(base64Encoded: "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM=")!
    private let encryptionKey = Data(base64Encoded: "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w")!

    private var fileSecret: DeviceFileSecret {
        DeviceFileSecret(file: "/tmp/some/file", iv: encryptionIv, key: encryptionKey)
    }

    private var metadataSecret: DeviceMetadataSecret {
        DeviceMetadataSecret(iv: encryptionIv, key: encryptionKey)
    }

    @Test("exposes IV size constant (12 bytes)")
    func ivSizeConstant() {
        #expect(Aes.ivSize == 12)
    }

    @Test("exposes tag size constant (128 bits)")
    func tagSizeConstant() {
        #expect(Aes.tagSize == 128)
    }

    @Test("exposes maximum plaintext size (4 GB)")
    func maximumPlaintextSizeConstant() {
        #expect(Aes.maximumPlaintextSize == 4 * 1024 * 1024 * 1024)
    }

    @Test("encrypts and decrypts arbitrary data (low-level)")
    func lowLevelRoundTrip() throws {
        let plaintext = Data("the quick brown fox".utf8)
        let ciphertext = try Aes.encrypt(plaintext: plaintext, key: encryptionKey, iv: encryptionIv)
        #expect(ciphertext != plaintext)
        let decrypted = try Aes.decrypt(ciphertext: ciphertext, key: encryptionKey, iv: encryptionIv)
        #expect(decrypted == plaintext)
    }

    @Test("rejects ciphertext shorter than the auth tag")
    func rejectsShortCiphertext() {
        #expect(throws: AesError.ciphertextTooShort) {
            _ = try Aes.decrypt(ciphertext: Data([0x01, 0x02]), key: encryptionKey, iv: encryptionIv)
        }
    }

    @Test("encrypts files via DeviceFileSecret")
    func encryptsFilesViaFileSecret() throws {
        let plaintext = EncryptionResources.load("plaintext-file")
        let expected = EncryptionResources.load("encrypted-file")
        let actual = try Aes.encrypt(plaintext, fileSecret: fileSecret)
        #expect(actual == expected)
    }

    @Test("decrypts files via DeviceFileSecret")
    func decryptsFilesViaFileSecret() throws {
        let encrypted = EncryptionResources.load("encrypted-file")
        let expected = EncryptionResources.load("plaintext-file")
        let actual = try Aes.decrypt(encrypted, fileSecret: fileSecret)
        #expect(actual == expected)
    }

    @Test("encrypts and decrypts via DeviceMetadataSecret")
    func roundTripViaMetadataSecret() throws {
        let plaintext = Data("some metadata payload".utf8)
        let ciphertext = try Aes.encrypt(plaintext, metadataSecret: metadataSecret)
        let decrypted = try Aes.decrypt(ciphertext, metadataSecret: metadataSecret)
        #expect(decrypted == plaintext)
    }

    @Test("encrypts and decrypts dataset metadata (round-trip)")
    func roundTripsDatasetMetadata() throws {
        let metadata = Self.datasetMetadataFixture
        let bytes = try metadata.toByteString()
        let encrypted = try Aes.encrypt(bytes, metadataSecret: metadataSecret)
        let decryptedBytes = try Aes.decrypt(encrypted, metadataSecret: metadataSecret)
        let actual = try DatasetMetadata(byteString: decryptedBytes)
        #expect(actual == metadata)
    }

    @Test("decrypts dataset metadata produced by other clients")
    func decryptsCrossPlatformDatasetMetadata() throws {
        let ciphertext = Data(base64Encoded: Self.crossPlatformEncryptedDatasetMetadata)!
        let decryptedBytes = try Aes.decrypt(ciphertext, metadataSecret: metadataSecret)
        let actual = try DatasetMetadata(byteString: decryptedBytes)
        #expect(actual == Self.crossPlatformDatasetMetadata)
    }

    // MARK: - cross-platform dataset-metadata fixtures
    //
    // The ciphertext below was produced by client-android using the same key/IV as `metadataSecret`
    // above and the metadata represented by `crossPlatformDatasetMetadata`. This validates that iOS can
    // read dataset metadata written by another client.
    private static let crossPlatformEncryptedDatasetMetadata =
        "HmbhwaypOBcEU1AhuKsDjI0+veHe83EmsKgSFnSfI8sd" +
        "L21k3hGfWZ/nv8j3Wdd1s2aatGFivHDQqIEy/XbEO/" +
        "t57k/hjMsj1x15u++p9cbXllxAWEvACpr8AtoCehZk" +
        "I9TankVFir2Yfr/N7tpMdNNw/UmA3Cord1AOZ02eRK" +
        "mbP7xT4Ppbu17AnAD4zNomR4txkDe7Xdr0eyHqtXsW" +
        "okosNgSd+QZXe/G374c1MGOMbBLfPICjxfxCUOkgG/" +
        "1pogMMHNTQM8HrwNqweP1h4n9eEjoK4fQpcSwwwspG" +
        "beplgBGjNJTZDGN5N83TMunCcgk5P5KQTKXh77Mpyv" +
        "pALCOEHukCm0dWCyqGSGAUKsl0oP3zCoBxY+gTsnHo" +
        "ock2RLli8jS2KLGt93LeQDuu3sM="

    // Date components encoded with the same epoch-second values as the source-of-truth Android fixtures
    // (`Instant.MIN/MAX` truncated to seconds). Using these here keeps the proto round-trip equal even
    // across the JVM/Swift Date precision boundary.
    private static let crossPlatformDatasetMetadata: DatasetMetadata = {
        let earliest = Date(timeIntervalSince1970: -31_557_014_167_219_200)
        let latest = Date(timeIntervalSince1970: 31_556_889_864_403_199)

        let fileOne = EntityMetadata.file(.init(
            path: "/tmp/file/one", link: nil, isHidden: false,
            created: earliest, updated: latest,
            owner: "root", group: "root", permissions: "rwxrwxrwx",
            size: 1, checksum: Data([0x01]),
            crates: ["/tmp/file/one_0": UUID(uuidString: "329efbeb-80a3-42b8-b1dc-79bc0fea7bca")!],
            compression: "none"
        ))
        let fileTwo = EntityMetadata.file(.init(
            path: "/tmp/file/two", link: "/tmp/file/three", isHidden: false,
            created: latest, updated: earliest,
            owner: "root", group: "root", permissions: "rwxrwxrwx",
            size: 2, checksum: Data([0x2a]),
            crates: ["/tmp/file/two_0": UUID(uuidString: "e672a956-1a95-4304-8af0-9418f0e43cba")!],
            compression: "gzip"
        ))
        let directoryOne = EntityMetadata.directory(.init(
            path: "/tmp/directory/one", link: nil, isHidden: false,
            created: earliest, updated: latest,
            owner: "root", group: "root", permissions: "rwxrwxrwx"
        ))
        let directoryTwo = EntityMetadata.directory(.init(
            path: "/tmp/directory/two", link: "/tmp/file/three", isHidden: false,
            created: latest, updated: earliest,
            owner: "root", group: "root", permissions: "rwxrwxrwx"
        ))

        return DatasetMetadata(
            contentChanged: [fileOne.path: fileOne],
            metadataChanged: [
                fileTwo.path: fileTwo,
                directoryOne.path: directoryOne,
                directoryTwo.path: directoryTwo
            ],
            filesystem: FilesystemMetadata(entities: [
                fileOne.path: .new,
                fileTwo.path: .updated,
                directoryOne.path: .new,
                directoryTwo.path: .new
            ])
        )
    }()

    private static let datasetMetadataFixture: DatasetMetadata = DatasetMetadata(
        contentChanged: [Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne],
        metadataChanged: [
            Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo,
            Fixtures.Metadata.directoryOne.path: Fixtures.Metadata.directoryOne,
            Fixtures.Metadata.directoryTwo.path: Fixtures.Metadata.directoryTwo
        ],
        filesystem: FilesystemMetadata(entities: [
            Fixtures.Metadata.fileOne.path: .new,
            Fixtures.Metadata.fileTwo.path: .updated,
            Fixtures.Metadata.directoryOne.path: .new,
            Fixtures.Metadata.directoryTwo.path: .new
        ])
    )
}

import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("EntityContent")
struct EntityContentTests {
    private let deviceSecret = DeviceSecret(
        user: SecretsConfigFixtures.testUser,
        device: SecretsConfigFixtures.testDevice,
        secret: Data(base64Encoded: "BOunLSLKxVbluhDSPZ/wWw==")!,
        target: SecretsConfigFixtures.testConfig
    )

    private func encrypted(_ plaintext: Data) -> Data {
        Data([MockEncrypting.sentinel]) + plaintext
    }

    private func fileMetadata(size: Int64, crates: [String: CrateId]) -> EntityMetadata.File {
        EntityMetadata.File(
            path: "/tmp/test",
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            owner: "test",
            group: "test",
            permissions: "rw-r--r--",
            size: size,
            checksum: Data([0x2A]),
            crates: crates,
            compression: Identity.shared.name
        )
    }

    private func libraryMetadata(size: Int64, crates: [String: CrateId]) -> EntityMetadata.Library {
        EntityMetadata.Library(
            path: "contacts:/test",
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            size: size,
            checksum: Data([0x2A]),
            crates: crates,
            compression: Identity.shared.name,
            attributes: Data()
        )
    }

    @Test("pulls, decrypts and decompresses a single-part filesystem entity")
    func pullsSinglePartFile() async throws {
        let original = Data("test".utf8)
        let crate: CrateId = UUID(uuidString: "0dd2b8f2-33cc-4f5c-9b6d-2ec33ac9f0a1")!
        let core = MockServerCoreEndpointClient(crates: [crate: encrypted(original)])
        let metadata = fileMetadata(size: Int64(original.count), crates: ["/tmp/test__part=0": crate])

        let stream = try await EntityContent.pull(
            metadata: metadata,
            entityKey: metadata.path,
            deviceSecret: deviceSecret,
            clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
            decryptor: MockDecrypting(),
            onPartProcessed: {}
        )

        #expect(try await collectData(stream) == original)
    }

    @Test("merges multi-part content in part order regardless of crate map order")
    func mergesMultiPartInOrder() async throws {
        let partA = Data("test a".utf8)
        let partB = Data("test b".utf8)
        let crateA: CrateId = UUID(uuidString: "1c1a2b3c-0000-4000-8000-000000000000")!
        let crateB: CrateId = UUID(uuidString: "2d2b3c4d-0000-4000-8000-000000000000")!
        let core = MockServerCoreEndpointClient(crates: [
            crateA: encrypted(partA),
            crateB: encrypted(partB)
        ])
        let metadata = libraryMetadata(
            size: Int64(partA.count + partB.count),
            crates: [
                "contacts:/test__part=1": crateB,
                "contacts:/test__part=0": crateA
            ]
        )

        let data = try await EntityContent.pullBytes(
            metadata: metadata,
            entityKey: metadata.path,
            deviceSecret: deviceSecret,
            clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
            decryptor: MockDecrypting(),
            onPartProcessed: {}
        )

        #expect(data == partA + partB)
    }

    @Test("invokes the part-processed callback once per crate")
    func reportsPartProgress() async throws {
        let crateA: CrateId = UUID(uuidString: "3e3c4d5e-0000-4000-8000-000000000000")!
        let crateB: CrateId = UUID(uuidString: "4f4d5e6f-0000-4000-8000-000000000000")!
        let core = MockServerCoreEndpointClient(crates: [
            crateA: encrypted(Data("test a".utf8)),
            crateB: encrypted(Data("test b".utf8))
        ])
        let metadata = libraryMetadata(
            size: 12,
            crates: [
                "contacts:/test__part=0": crateA,
                "contacts:/test__part=1": crateB
            ]
        )
        let processed = Counter()

        let data = try await EntityContent.pullBytes(
            metadata: metadata,
            entityKey: metadata.path,
            deviceSecret: deviceSecret,
            clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
            decryptor: MockDecrypting(),
            onPartProcessed: { processed.increment() }
        )

        #expect(!data.isEmpty)
        #expect(processed.value == 2)
    }

    @Test("throws when a crate is missing from the core")
    func throwsOnMissingCrate() async throws {
        let crate: CrateId = UUID(uuidString: "5a5e6f7a-0000-4000-8000-000000000000")!
        let core = MockServerCoreEndpointClient(crates: [:])
        let metadata = fileMetadata(size: 4, crates: ["/tmp/test__part=0": crate])

        await #expect(throws: RecoveryPullError.crateMissing(crate: crate, entity: "/tmp/test")) {
            let stream = try await EntityContent.pull(
                metadata: metadata,
                entityKey: metadata.path,
                deviceSecret: deviceSecret,
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                decryptor: MockDecrypting(),
                onPartProcessed: {}
            )
            _ = try await collectData(stream)
        }
    }

    @Test("throws when the highest part id does not match the crate count")
    func throwsOnPartGap() async throws {
        let crate: CrateId = UUID(uuidString: "6b6f7a8b-0000-4000-8000-000000000000")!
        let core = MockServerCoreEndpointClient(crates: [crate: encrypted(Data("test".utf8))])
        let metadata = fileMetadata(size: 4, crates: ["/tmp/test__part=3": crate])

        await #expect(throws: RecoveryPullError.unexpectedLastPartId(lastPartId: 3, crateCount: 1)) {
            _ = try await EntityContent.pull(
                metadata: metadata,
                entityKey: metadata.path,
                deviceSecret: deviceSecret,
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                decryptor: MockDecrypting(),
                onPartProcessed: {}
            )
        }
    }
}

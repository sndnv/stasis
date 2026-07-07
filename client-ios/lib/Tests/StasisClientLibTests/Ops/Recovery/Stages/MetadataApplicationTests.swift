import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("Recovery.MetadataApplication stage")
struct MetadataApplicationTests {
    @Test("applies metadata to files")
    func appliesMetadata() async throws {
        let fs = try TempFilesystem()
        let target = try fs.createFile("metadata-target-file", contents: Data("x".utf8))

        let beforeUpdated = try Date.metadataUpdated(of: target)

        let baseMetadata = try await Metadata.extractBaseEntityMetadata(entity: target)
        let metadata = EntityMetadata.file(.init(
            path: target.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 1_577_836_800),
            updated: Date(timeIntervalSince1970: 1_578_009_600),
            owner: baseMetadata.owner,
            group: baseMetadata.group,
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: Data([0x01]),
            crates: ["\(target.path)_0": UUID()],
            compression: "none"
        ))

        let tracker = MockRecoveryTracker()
        let stage = Recovery.MetadataApplication(providers: makeProviders(tracker: tracker))

        let targetEntity = try TargetEntity(
            ref: .filesystem(target),
            destination: .default,
            existingMetadata: metadata,
            currentMetadata: nil
        )

        let upstream = AsyncThrowingStream<TargetEntity, Error> { continuation in
            continuation.yield(targetEntity)
            continuation.finish()
        }

        var emitted: [TargetEntity] = []
        for try await entity in stage.apply(operation: UUID(), entities: upstream) {
            emitted.append(entity)
        }

        #expect(emitted == [targetEntity])

        let after = try await Metadata.extractBaseEntityMetadata(entity: target)
        #expect(after.permissions == "rwxrwxrwx")
        #expect(after.updated != beforeUpdated)

        #expect(tracker.statistics[.metadataApplied] == 1)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    private func makeProviders(tracker: any RecoveryTracker) -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            decryptor: MockDecrypting(),
            clients: StaticClients(
                api: MockServerApiEndpointClient(),
                core: MockServerCoreEndpointClient()
            ),
            track: tracker,
            analytics: NoOpAnalyticsCollector(),
            kinds: [RecoveryEntityKinds.filesystem]
        )
    }
}

private extension Date {
    static func metadataUpdated(of url: URL) throws -> Date {
        let attrs = try FileManager.default.attributesOfItem(atPath: url.path)
        return (attrs[.modificationDate] as? Date) ?? Date()
    }
}

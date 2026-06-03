import Foundation
@testable import StasisClientLib
import Testing

@Suite("Recovery.EntityProcessing stage")
struct RecoveryEntityProcessingTests {
    @Test("extracts part IDs from a path")
    func extractsPartIds() {
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__part=1") == 1)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__part=12324") == 12324)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__part=0") == 0)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__part=-1") == 0)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__part=") == 0)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__part") == 0)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__") == 0)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a_") == 0)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a") == 0)
        #expect(Recovery.EntityProcessing.partIdFromPath("/tmp/a__part=other") == 0)
    }

    @Test("fails if directory metadata is provided to expectFileMetadata")
    func expectFileMetadataFailsForDirectory() throws {
        let entity = try TargetEntity(
            path: URL(fileURLWithPath: Fixtures.Metadata.directoryOne.path),
            destination: .default,
            existingMetadata: Fixtures.Metadata.directoryOne,
            currentMetadata: nil
        )
        #expect(throws: Recovery.EntityProcessingError.expectedFileGotDirectory(path: Fixtures.Metadata.directoryOne.path)) {
            _ = try Recovery.EntityProcessing.expectFileMetadata(entity: entity)
        }
    }

    @Test("processes a single content-changed entity")
    func processesContentChanged() async throws {
        let fs = try TempFilesystem()
        let destinationPath = fs.resolve("recovered/source-file-1")
        let crateId = UUID()
        let partKey = "/ops/source-file-1__part=0"
        let plaintext = Data("recovered-bytes".utf8)
        let ciphertext = Data([MockEncrypting.sentinel]) + plaintext

        let existing = EntityMetadata.file(.init(
            path: destinationPath.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: Int64(plaintext.count),
            checksum: Data([0xAA]),
            crates: [partKey: crateId],
            compression: "none"
        ))
        let current = existing.withFileFlags(checksum: Data([0x99]))

        let target = try TargetEntity(
            path: destinationPath,
            destination: .default,
            existingMetadata: existing,
            currentMetadata: current
        )

        let staging = MockFileStaging()
        let decryption = MockDecrypting()
        let core = MockServerCoreEndpointClient(crates: [crateId: ciphertext])
        let tracker = MockRecoveryTracker()

        let stage = Recovery.EntityProcessing(
            deviceSecret: Fixtures.Secrets.default,
            providers: RecoveryProviders(
                checksum: Checksums.md5, staging: staging, compression: MockCompression(),
                decryptor: decryption,
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                track: tracker, analytics: NoOpAnalyticsCollector()
            )
        )

        let upstream = AsyncThrowingStream<TargetEntity, Error> { continuation in
            continuation.yield(target)
            continuation.finish()
        }

        var emitted: [TargetEntity] = []
        for try await entity in stage.process(operation: UUID(), entities: upstream) {
            emitted.append(entity)
        }

        #expect(emitted == [target])
        #expect(staging.statistics.destaged == 1)
        #expect(decryption.calls.fileSecret.count == 1)
        let pullCount = await core.pullCount
        #expect(pullCount == 1)
        #expect(tracker.statistics[.entityProcessingStarted] == 1)
        #expect(tracker.statistics[.entityPartProcessed] == 1)
        #expect(tracker.statistics[.entityProcessed] == 1)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    @Test("fails when crates cannot be pulled")
    func failsOnPull() async throws {
        let fs = try TempFilesystem()
        let path = fs.resolve("source-file-1")
        let crateId = UUID()
        let partKey = "\(path.path)__part=0"
        let existing = makeFileMetadata(path: path.path, crates: [partKey: crateId])
        let target = try TargetEntity(
            path: path,
            destination: .default,
            existingMetadata: existing,
            currentMetadata: existing.withFileFlags(checksum: Data([0x99]))
        )

        let tracker = MockRecoveryTracker()
        let core = MockServerCoreEndpointClient(pullDisabled: true)
        let stage = Recovery.EntityProcessing(
            deviceSecret: Fixtures.Secrets.default,
            providers: RecoveryProviders(
                checksum: Checksums.md5, staging: MockFileStaging(), compression: MockCompression(),
                decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                track: tracker, analytics: NoOpAnalyticsCollector()
            )
        )

        let upstream = AsyncThrowingStream<TargetEntity, Error> { continuation in
            continuation.yield(target)
            continuation.finish()
        }
        await #expect(throws: EndpointFailure.self) {
            for try await _ in stage.process(operation: UUID(), entities: upstream) {}
        }
        #expect(tracker.statistics[.failureEncountered] == 1)
        #expect(tracker.statistics[.entityProcessed] == 0)
    }

    @Test("silently drops entity on generic processing failures")
    func dropsOnGenericFailure() async throws {
        let fs = try TempFilesystem()
        let path = fs.resolve("source-file-1")
        let crateId = UUID()
        let partKey = "\(path.path)__part=0"
        let existing = makeFileMetadata(path: path.path, crates: [partKey: crateId])
        let target = try TargetEntity(
            path: path,
            destination: .default,
            existingMetadata: existing,
            currentMetadata: existing.withFileFlags(checksum: Data([0x99]))
        )

        let tracker = MockRecoveryTracker()
        let stage = Recovery.EntityProcessing(
            deviceSecret: Fixtures.Secrets.default,
            providers: RecoveryProviders(
                checksum: Checksums.md5, staging: MockFileStaging(), compression: FailingDecompression(),
                decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: MockServerCoreEndpointClient()),
                track: tracker, analytics: NoOpAnalyticsCollector()
            )
        )

        let upstream = AsyncThrowingStream<TargetEntity, Error> { continuation in
            continuation.yield(target)
            continuation.finish()
        }
        var emitted: [TargetEntity] = []
        for try await entity in stage.process(operation: UUID(), entities: upstream) {
            emitted.append(entity)
        }
        #expect(emitted.isEmpty)
        #expect(tracker.statistics[.entityProcessingStarted] == 1)
        #expect(tracker.statistics[.failureEncountered] == 1)
        #expect(tracker.statistics[.entityProcessed] == 0)
    }

    @Test("fails when crate map has gaps in part ids")
    func failsOnGappyCrates() async throws {
        let fs = try TempFilesystem()
        let path = fs.resolve("file")
        let crates: [String: CrateId] = [
            "\(path.path)__part=0": UUID(),
            "\(path.path)__part=1": UUID(),
            "\(path.path)__part=2": UUID(),
            "\(path.path)__part=5": UUID()
        ]
        let existing = makeFileMetadata(path: path.path, crates: crates)
        let target = try TargetEntity(
            path: path,
            destination: .default,
            existingMetadata: existing,
            currentMetadata: existing.withFileFlags(checksum: Data([0x99]))
        )

        let tracker = MockRecoveryTracker()
        let stage = Recovery.EntityProcessing(
            deviceSecret: Fixtures.Secrets.default,
            providers: RecoveryProviders(
                checksum: Checksums.md5, staging: MockFileStaging(), compression: MockCompression(),
                decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: MockServerCoreEndpointClient()),
                track: tracker, analytics: NoOpAnalyticsCollector()
            )
        )

        let upstream = AsyncThrowingStream<TargetEntity, Error> { continuation in
            continuation.yield(target)
            continuation.finish()
        }
        var emitted: [TargetEntity] = []
        for try await entity in stage.process(operation: UUID(), entities: upstream) {
            emitted.append(entity)
        }
        #expect(emitted.isEmpty)
        #expect(tracker.statistics[.failureEncountered] == 1)
    }

    @Test("fails when no crates are provided")
    func failsOnNoCrates() async throws {
        let fs = try TempFilesystem()
        let path = fs.resolve("file")
        let existing = makeFileMetadata(path: path.path, crates: [:])
        let target = try TargetEntity(
            path: path,
            destination: .default,
            existingMetadata: existing,
            currentMetadata: existing.withFileFlags(checksum: Data([0x99]))
        )

        let tracker = MockRecoveryTracker()
        let stage = Recovery.EntityProcessing(
            deviceSecret: Fixtures.Secrets.default,
            providers: RecoveryProviders(
                checksum: Checksums.md5, staging: MockFileStaging(), compression: MockCompression(),
                decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: MockServerCoreEndpointClient()),
                track: tracker, analytics: NoOpAnalyticsCollector()
            )
        )

        let upstream = AsyncThrowingStream<TargetEntity, Error> { continuation in
            continuation.yield(target)
            continuation.finish()
        }
        var emitted: [TargetEntity] = []
        for try await entity in stage.process(operation: UUID(), entities: upstream) {
            emitted.append(entity)
        }
        #expect(emitted.isEmpty)
        #expect(tracker.statistics[.failureEncountered] == 1)
    }

    @Test("drops directory entities when destination keeps no default structure")
    func dropsDirectoryWithoutKeepingStructure() async throws {
        let fs = try TempFilesystem()
        let destination = fs.resolve("dest")
        let plaintext = Data("recovered-file-bytes".utf8)
        let ciphertext = Data([MockEncrypting.sentinel]) + plaintext

        // file entity routed to a Directory destination with !keepDefaultStructure — kept, processed
        let fileCrate = UUID()
        let filePath = "/source/file.bin"
        let filePartKey = "\(filePath)__part=0"
        let fileMetadata = makeFileMetadata(path: filePath, crates: [filePartKey: fileCrate])
        let fileTarget = try TargetEntity(
            path: URL(fileURLWithPath: filePath),
            destination: .directory(path: destination, keepDefaultStructure: false),
            existingMetadata: fileMetadata,
            currentMetadata: fileMetadata.withFileFlags(checksum: Data([0x99]))
        )

        // directory entity routed to a Directory destination with !keepDefaultStructure — dropped
        let dirPath = "/source/some-dir"
        let dirMetadata = EntityMetadata.directory(.init(
            path: dirPath, link: nil, isHidden: false,
            created: Date(timeIntervalSince1970: 0), updated: Date(timeIntervalSince1970: 0),
            owner: "root", group: "root", permissions: "rwxrwxrwx"
        ))
        let dirTarget = try TargetEntity(
            path: URL(fileURLWithPath: dirPath),
            destination: .directory(path: destination, keepDefaultStructure: false),
            existingMetadata: dirMetadata,
            currentMetadata: nil
        )

        let staging = MockFileStaging()
        let core = MockServerCoreEndpointClient(crates: [fileCrate: ciphertext])
        let tracker = MockRecoveryTracker()
        let stage = Recovery.EntityProcessing(
            deviceSecret: Fixtures.Secrets.default,
            providers: RecoveryProviders(
                checksum: Checksums.md5, staging: staging, compression: MockCompression(),
                decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                track: tracker, analytics: NoOpAnalyticsCollector()
            )
        )

        let upstream = AsyncThrowingStream<TargetEntity, Error> { continuation in
            continuation.yield(fileTarget)
            continuation.yield(dirTarget)
            continuation.finish()
        }

        var emitted: [TargetEntity] = []
        for try await entity in stage.process(operation: UUID(), entities: upstream) {
            emitted.append(entity)
        }

        #expect(emitted == [fileTarget])
        #expect(tracker.statistics[.entityProcessingStarted] == 1)
        #expect(tracker.statistics[.entityProcessed] == 1)
        let pullCount = await core.pullCount
        #expect(pullCount == 1)
    }

    private func makeFileMetadata(path: String, crates: [String: CrateId]) -> EntityMetadata {
        EntityMetadata.file(.init(
            path: path, link: nil, isHidden: false,
            created: Date(timeIntervalSince1970: 0), updated: Date(timeIntervalSince1970: 0),
            owner: "root", group: "root", permissions: "rwxrwxrwx",
            size: 0, checksum: Data([0xAA]),
            crates: crates, compression: "none"
        ))
    }
}

private struct FailingDecompression: Compression {
    let defaultCompression: any Compressor = Identity.shared
    let disabledExtensions: Set<String> = []

    func decoderFor(entity: TargetEntity) throws -> any CompressionDecoder {
        throw TestFailure(message: "Test failure")
    }
}

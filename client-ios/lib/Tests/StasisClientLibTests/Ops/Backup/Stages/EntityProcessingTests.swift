import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("EntityProcessing stage")
struct EntityProcessingTests {
    @Test("extract and expect content metadata")
    func expectsContentMetadata() throws {
        let entity = try SourceEntity(
            ref: .filesystem(URL(fileURLWithPath: Fixtures.Metadata.fileOne.path)),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileOne
        )
        guard case .file(let expected) = Fixtures.Metadata.fileOne else {
            Issue.record("expected file metadata fixture")
            return
        }
        let actual = try Backup.EntityProcessing.expectContentMetadata(entity: entity)
        #expect(actual as? EntityMetadata.File == expected)
    }

    @Test("fail if unexpected target entity metadata is provided")
    func failsForDirectory() throws {
        let entity = try SourceEntity(
            ref: .filesystem(URL(fileURLWithPath: Fixtures.Metadata.directoryOne.path)),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.directoryOne
        )
        #expect(throws: Backup.EntityProcessingError.expectedFileGotDirectory(path: Fixtures.Metadata.directoryOne.path)) {
            _ = try Backup.EntityProcessing.expectContentMetadata(entity: entity)
        }
    }

    @Test("calculate expected parts for an entity")
    func computesExpectedParts() throws {
        guard case .file(var fileMeta) = Fixtures.Metadata.fileOne else {
            Issue.record("expected file metadata")
            return
        }
        fileMeta = .init(
            path: fileMeta.path, link: fileMeta.link, isHidden: fileMeta.isHidden,
            created: fileMeta.created, updated: fileMeta.updated, owner: fileMeta.owner,
            group: fileMeta.group, permissions: fileMeta.permissions,
            size: 10, checksum: fileMeta.checksum, crates: fileMeta.crates, compression: fileMeta.compression
        )
        let fileEntity = try SourceEntity(
            ref: .filesystem(URL(fileURLWithPath: fileMeta.path)),
            existingMetadata: nil,
            currentMetadata: .file(fileMeta)
        )
        let directoryEntity = try SourceEntity(
            ref: .filesystem(URL(fileURLWithPath: Fixtures.Metadata.directoryOne.path)),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.directoryOne
        )
        let unchangedFileEntity = try SourceEntity(
            ref: .filesystem(URL(fileURLWithPath: fileMeta.path)),
            existingMetadata: .file(fileMeta),
            currentMetadata: .file(fileMeta)
        )

        #expect(throws: Backup.EntityProcessingError.invalidMaximumPartSize(0)) {
            _ = try Backup.EntityProcessing.expectedParts(entity: fileEntity, withMaximumPartSize: 0)
        }

        #expect(try Backup.EntityProcessing.expectedParts(entity: fileEntity, withMaximumPartSize: 1) == 10)
        #expect(try Backup.EntityProcessing.expectedParts(entity: fileEntity, withMaximumPartSize: 3) == 4)
        #expect(try Backup.EntityProcessing.expectedParts(entity: fileEntity, withMaximumPartSize: 10) == 1)
        #expect(try Backup.EntityProcessing.expectedParts(entity: fileEntity, withMaximumPartSize: 11) == 1)
        #expect(try Backup.EntityProcessing.expectedParts(entity: directoryEntity, withMaximumPartSize: 10) == 0)
        #expect(try Backup.EntityProcessing.expectedParts(entity: unchangedFileEntity, withMaximumPartSize: 10) == 0)
    }

    @Test("processes a file with changed content")
    func processesContentChanged() async throws {
        let sourceFile = OpsResources.url("source-file-1")
        let fileSize = try Int64(Data(contentsOf: sourceFile).count)

        let staging = MockFileStaging()
        let encryption = MockEncrypting()
        let core = MockServerCoreEndpointClient()
        let tracker = MockBackupTracker()

        guard case .file(let baseFile) = Fixtures.Metadata.fileOne else {
            Issue.record("expected file fixture")
            return
        }
        let currentMetadata = EntityMetadata.file(.init(
            path: sourceFile.path, link: nil, isHidden: false,
            created: baseFile.created, updated: baseFile.updated,
            owner: baseFile.owner, group: baseFile.group, permissions: baseFile.permissions,
            size: fileSize, checksum: Data([0xAA]),
            crates: [:], compression: "none"
        ))
        let entity = try SourceEntity(ref: .filesystem(sourceFile), existingMetadata: nil, currentMetadata: currentMetadata)

        let stage = Backup.EntityProcessing(
            targetDataset: Fixtures.Datasets.default,
            deviceSecret: Fixtures.Secrets.default,
            providers: BackupProviders(
                checksum: Checksums.md5,
                staging: staging,
                compression: MockCompression(),
                encryptor: encryption,
                decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                track: tracker,
                analytics: NoOpAnalyticsCollector(),
                kinds: [BackupEntityKinds.filesystem]
            ),
            maxPartSize: 16_384,
            maxChunkSize: 8_192
        )

        let upstream = AsyncThrowingStream<SourceEntity, Error> { continuation in
            continuation.yield(entity)
            continuation.finish()
        }

        var outputs: [Either<EntityMetadata, EntityMetadata>] = []
        for try await result in stage.process(operation: UUID(), entities: upstream) {
            outputs.append(result)
        }

        #expect(outputs.count == 1)
        guard let firstOutput = outputs.first,
              case .left(let entityMetadata) = firstOutput,
              case .file(let resultFile) = entityMetadata else {
            Issue.record("expected Left/file result")
            return
        }
        #expect(resultFile.crates.count == 1)

        #expect(staging.statistics.temporaryCreated == 1)
        #expect(staging.statistics.temporaryDiscarded == 1)
        #expect(staging.statistics.destaged == 0)
        #expect(encryption.calls.fileSecret.count == 1)

        let pushCount = await core.pushCount
        #expect(pushCount == 1)

        #expect(tracker.statistics[.entityProcessingStarted] == 1)
        #expect(tracker.statistics[.entityPartProcessed] == 1)
        #expect(tracker.statistics[.entityProcessed] == 1)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    @Test("processes mixed content-changed and metadata-only entities")
    func processesMixedEntities() async throws {
        let sourceFile1 = OpsResources.url("source-file-1")
        let sourceFile2 = OpsResources.url("source-file-2")
        let sourceFile3 = OpsResources.url("source-file-3")

        let staging = MockFileStaging()
        let encryption = MockEncrypting()
        let core = MockServerCoreEndpointClient()
        let tracker = MockBackupTracker()

        guard case .file(let baseFile) = Fixtures.Metadata.fileOne else {
            Issue.record("expected file fixture"); return
        }

        let metadata1 = makeFileMetadata(path: sourceFile1.path, baseFile: baseFile, checksum: Data([0xAA]))
        let metadata2 = makeFileMetadata(path: sourceFile2.path, baseFile: baseFile, checksum: Data([0xBB]))
        let metadata3 = makeFileMetadata(path: sourceFile3.path, baseFile: baseFile, checksum: Data([0xCC]))

        let entity1 = try SourceEntity(ref: .filesystem(sourceFile1), existingMetadata: nil, currentMetadata: metadata1)
        let entity2 = try SourceEntity(
            ref: .filesystem(sourceFile2),
            existingMetadata: metadata2.withFileFlags(isHidden: true),
            currentMetadata: metadata2
        )
        let entity3 = try SourceEntity(
            ref: .filesystem(sourceFile3),
            existingMetadata: metadata3.withFileFlags(checksum: Data([0x99])),
            currentMetadata: metadata3
        )

        let stage = Backup.EntityProcessing(
            targetDataset: Fixtures.Datasets.default,
            deviceSecret: Fixtures.Secrets.default,
            providers: BackupProviders(
                checksum: Checksums.md5, staging: staging, compression: MockCompression(),
                encryptor: encryption, decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                track: tracker, analytics: NoOpAnalyticsCollector(),
                kinds: [BackupEntityKinds.filesystem]
            ),
            maxPartSize: 16_384,
            maxChunkSize: 8_192
        )

        let upstream = AsyncThrowingStream<SourceEntity, Error> { continuation in
            continuation.yield(entity1)
            continuation.yield(entity2)
            continuation.yield(entity3)
            continuation.finish()
        }

        var outputs: [Either<EntityMetadata, EntityMetadata>] = []
        for try await result in stage.process(operation: UUID(), entities: upstream) {
            outputs.append(result)
        }

        #expect(outputs.count == 3)
        #expect(outputs[0].isLeft)   // content-changed
        #expect(outputs[1].isRight)  // metadata-only
        #expect(outputs[2].isLeft)   // content-changed (different checksum)

        #expect(staging.statistics.temporaryCreated == 2)
        #expect(staging.statistics.temporaryDiscarded == 2)
        #expect(encryption.calls.fileSecret.count == 2)
        let pushCount = await core.pushCount
        #expect(pushCount == 2)
        #expect(tracker.statistics[.entityProcessingStarted] == 3)
        #expect(tracker.statistics[.entityPartProcessed] == 2)
        #expect(tracker.statistics[.entityProcessed] == 3)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    @Test("drops the entity when a non-endpoint failure occurs")
    func dropsOnGenericFailure() async throws {
        let sourceFile = OpsResources.url("source-file-1")

        let staging = MockFileStaging()
        let encryption = MockEncrypting()
        let core = MockServerCoreEndpointClient()
        let tracker = MockBackupTracker()
        let failingCompression = FailingCompression()

        guard case .file(let baseFile) = Fixtures.Metadata.fileOne else {
            Issue.record("expected file fixture"); return
        }
        let currentMetadata = makeFileMetadata(path: sourceFile.path, baseFile: baseFile, checksum: Data([0xAA]))
        let entity = try SourceEntity(ref: .filesystem(sourceFile), existingMetadata: nil, currentMetadata: currentMetadata)

        let stage = Backup.EntityProcessing(
            targetDataset: Fixtures.Datasets.default,
            deviceSecret: Fixtures.Secrets.default,
            providers: BackupProviders(
                checksum: Checksums.md5, staging: staging, compression: failingCompression,
                encryptor: encryption, decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                track: tracker, analytics: NoOpAnalyticsCollector(),
                kinds: [BackupEntityKinds.filesystem]
            ),
            maxPartSize: 16_384,
            maxChunkSize: 8_192
        )

        let upstream = AsyncThrowingStream<SourceEntity, Error> { continuation in
            continuation.yield(entity)
            continuation.finish()
        }

        var outputs: [Either<EntityMetadata, EntityMetadata>] = []
        for try await result in stage.process(operation: UUID(), entities: upstream) {
            outputs.append(result)
        }
        #expect(outputs.isEmpty)
        #expect(staging.statistics.temporaryCreated == 0)
        let pushCount = await core.pushCount
        #expect(pushCount == 0)
        #expect(tracker.statistics[.entityProcessed] == 0)
        #expect(tracker.statistics[.failureEncountered] == 1)
    }

    private func makeFileMetadata(path: String, baseFile: EntityMetadata.File, checksum: Data) -> EntityMetadata {
        let fileSize: Int64 = (try? Int64(Data(contentsOf: URL(fileURLWithPath: path)).count)) ?? 0
        return EntityMetadata.file(.init(
            path: path, link: nil, isHidden: false,
            created: baseFile.created, updated: baseFile.updated,
            owner: baseFile.owner, group: baseFile.group, permissions: baseFile.permissions,
            size: fileSize, checksum: checksum, crates: [:], compression: "none"
        ))
    }

    @Test("fails on push failures")
    func failsOnPush() async throws {
        let sourceFile = OpsResources.url("source-file-1")
        let fileSize = try Int64(Data(contentsOf: sourceFile).count)

        let staging = MockFileStaging()
        let encryption = MockEncrypting()
        let core = MockServerCoreEndpointClient(pushDisabled: true)
        let tracker = MockBackupTracker()

        guard case .file(let baseFile) = Fixtures.Metadata.fileOne else {
            Issue.record("expected file fixture")
            return
        }
        let currentMetadata = EntityMetadata.file(.init(
            path: sourceFile.path, link: nil, isHidden: false,
            created: baseFile.created, updated: baseFile.updated,
            owner: baseFile.owner, group: baseFile.group, permissions: baseFile.permissions,
            size: fileSize, checksum: Data([0xAA]),
            crates: [:], compression: "none"
        ))
        let entity = try SourceEntity(ref: .filesystem(sourceFile), existingMetadata: nil, currentMetadata: currentMetadata)

        let stage = Backup.EntityProcessing(
            targetDataset: Fixtures.Datasets.default,
            deviceSecret: Fixtures.Secrets.default,
            providers: BackupProviders(
                checksum: Checksums.md5,
                staging: staging,
                compression: MockCompression(),
                encryptor: encryption,
                decryptor: MockDecrypting(),
                clients: StaticClients(api: MockServerApiEndpointClient(), core: core),
                track: tracker,
                analytics: NoOpAnalyticsCollector(),
                kinds: [BackupEntityKinds.filesystem]
            ),
            maxPartSize: 16_384,
            maxChunkSize: 8_192
        )

        let upstream = AsyncThrowingStream<SourceEntity, Error> { continuation in
            continuation.yield(entity)
            continuation.finish()
        }

        await #expect(throws: EndpointFailure.self) {
            for try await _ in stage.process(operation: UUID(), entities: upstream) {}
        }

        #expect(staging.statistics.temporaryCreated == 1)
        #expect(staging.statistics.temporaryDiscarded == 1)
        let pushCount = await core.pushCount
        #expect(pushCount == 0)
        #expect(tracker.statistics[.failureEncountered] == 1)
        #expect(tracker.statistics[.entityProcessed] == 0)
    }
}

private struct FailingCompression: Compression {
    let defaultCompression: any Compressor = Identity.shared
    let disabledExtensions: Set<String> = []

    func encoderFor(entity: SourceEntity) throws -> any CompressionEncoder {
        throw TestFailure(message: "Test failure")
    }
}

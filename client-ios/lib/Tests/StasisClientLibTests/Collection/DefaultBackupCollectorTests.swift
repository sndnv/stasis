import Foundation
@testable import StasisClientLib
import Testing

@Suite("DefaultBackupCollector")
struct DefaultBackupCollectorTests {
    @Test("collects backup files based on a files list")
    func collectsBackupFiles() async throws {
        let file1 = CollectionResources.url("file-1")
        let file2 = CollectionResources.url("file-2")

        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )

        let collector = DefaultBackupCollector(
            entities: [file1, file2],
            latestMetadata: .empty(),
            metadataCollector: DefaultBackupMetadataCollector(
                checksum: Checksums.md5,
                compression: MockCompression()
            ),
            clients: clients
        )

        var sourceFiles: [SourceEntity] = []
        for try await source in collector.collect() {
            sourceFiles.append(source)
        }
        sourceFiles.sort { $0.path.path < $1.path.path }

        #expect(sourceFiles.count == 2)
        #expect(sourceFiles[0].path == file1)
        #expect(sourceFiles[0].existingMetadata == nil)
        if case .file(let metadata) = sourceFiles[0].currentMetadata {
            #expect(metadata.size == 1)
        } else {
            Issue.record("expected file metadata, got directory")
        }

        #expect(sourceFiles[1].path == file2)
        #expect(sourceFiles[1].existingMetadata == nil)
        if case .file(let metadata) = sourceFiles[1].currentMetadata {
            #expect(metadata.size == 2)
        } else {
            Issue.record("expected file metadata, got directory")
        }
    }

    @Test("collects metadata for individual files")
    func collectsMetadataForIndividualFiles() async throws {
        let file1 = CollectionResources.url("file-1")
        let file1Metadata = Fixtures.Metadata.fileOne.with(path: file1.path)
        let file2 = CollectionResources.url("file-2")

        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )

        let latest = DatasetMetadata(
            contentChanged: [file1.path: file1Metadata],
            metadataChanged: [:],
            filesystem: FilesystemMetadata(entities: [file1.path: .new])
        )

        var collected: [(URL, EntityMetadata?)] = []
        for try await entry in DefaultBackupCollector.collectEntityMetadata(
            entities: [file1, file2],
            latestMetadata: latest,
            clients: clients
        ) {
            collected.append(entry)
        }

        #expect(collected.count == 2)
        #expect(collected[0].0 == file1)
        #expect(collected[0].1 == file1Metadata)
        #expect(collected[1].0 == file2)
        #expect(collected[1].1 == nil)
    }

    @Test("collects no file metadata if dataset metadata is not available")
    func collectsNoMetadataWhenAbsent() async throws {
        let file1 = CollectionResources.url("file-1")
        let file2 = CollectionResources.url("file-2")

        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )

        var collected: [(URL, EntityMetadata?)] = []
        for try await entry in DefaultBackupCollector.collectEntityMetadata(
            entities: [file1, file2],
            latestMetadata: nil,
            clients: clients
        ) {
            collected.append(entry)
        }

        #expect(collected.count == 2)
        #expect(collected[0].0 == file1)
        #expect(collected[0].1 == nil)
        #expect(collected[1].0 == file2)
        #expect(collected[1].1 == nil)
    }
}

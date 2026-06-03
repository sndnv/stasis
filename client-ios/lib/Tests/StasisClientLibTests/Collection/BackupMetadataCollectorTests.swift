import Foundation
@testable import StasisClientLib
import Testing

@Suite("BackupMetadataCollector")
struct BackupMetadataCollectorTests {
    @Test("collects file metadata (backup)")
    func collectsFileMetadata() async throws {
        let file1 = CollectionResources.url("file-1")
        let file2 = CollectionResources.url("file-2")
        let file3 = CollectionResources.url("file-3")

        let file2Metadata = Fixtures.Metadata.fileTwo.with(path: file2.path)
        let file3Metadata = Fixtures.Metadata.fileThree.with(path: file3.path)

        let collector = DefaultBackupMetadataCollector(
            checksum: Checksums.md5,
            compression: MockCompression()
        )

        let sourceFile1 = try await collector.collect(entity: file1, existingMetadata: nil)
        let sourceFile2 = try await collector.collect(entity: file2, existingMetadata: file2Metadata)
        let sourceFile3 = try await collector.collect(entity: file3, existingMetadata: file3Metadata)

        #expect(sourceFile1.path == file1)
        #expect(sourceFile1.existingMetadata == nil)
        if case .file(let metadata) = sourceFile1.currentMetadata {
            #expect(metadata.size == 1)
        } else {
            Issue.record("expected file metadata, got directory")
        }

        #expect(sourceFile2.path == file2)
        #expect(sourceFile2.existingMetadata == file2Metadata)
        if case .file(let metadata) = sourceFile2.currentMetadata {
            #expect(metadata.size == 2)
        } else {
            Issue.record("expected file metadata, got directory")
        }

        #expect(sourceFile3.path == file3)
        #expect(sourceFile3.existingMetadata == file3Metadata)
        if case .file(let metadata) = sourceFile3.currentMetadata {
            #expect(metadata.size == 3)
        } else {
            Issue.record("expected file metadata, got directory")
        }
    }
}

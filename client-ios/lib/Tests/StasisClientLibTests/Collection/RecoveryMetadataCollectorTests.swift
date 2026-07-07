import Foundation
@testable import StasisClientLib
import Testing

@Suite("RecoveryMetadataCollector")
struct RecoveryMetadataCollectorTests {
    @Test("collects file metadata (recovery)")
    func collectsFileMetadata() async throws {
        let file2 = CollectionResources.url("file-2")
        let file3 = CollectionResources.url("file-3")

        let collector = FilesystemRecoveryMetadataCollector(checksum: Checksums.md5)

        let file2Metadata = Fixtures.Metadata.fileTwo.with(path: file2.path)
        let file3Metadata = Fixtures.Metadata.fileThree.with(path: file3.path)

        let targetFile2 = try await collector.collect(
            entity: file2,
            destination: .default,
            existingMetadata: file2Metadata
        )

        let targetFile3 = try await collector.collect(
            entity: file3,
            destination: .default,
            existingMetadata: file3Metadata
        )

        #expect(targetFile2.ref == .filesystem(file2))
        #expect(targetFile2.existingMetadata == file2Metadata)
        switch targetFile2.currentMetadata {
        case .file(let metadata)?: #expect(metadata.size == 2)
        case .directory?: Issue.record("expected file metadata, got directory")
        case .library?: Issue.record("expected file metadata, got library")
        case nil: Issue.record("expected metadata but received none")
        }

        #expect(targetFile3.ref == .filesystem(file3))
        #expect(targetFile3.existingMetadata == file3Metadata)
        switch targetFile3.currentMetadata {
        case .file(let metadata)?: #expect(metadata.size == 3)
        case .directory?: Issue.record("expected file metadata, got directory")
        case .library?: Issue.record("expected file metadata, got library")
        case nil: Issue.record("expected metadata but received none")
        }
    }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("FilesystemMetadata")
struct FilesystemMetadataTests {
    private let entry = UUID()

    private func makeFilesystemMetadata() -> FilesystemMetadata {
        FilesystemMetadata(entities: [
            Fixtures.Metadata.fileOne.path: .new,
            Fixtures.Metadata.fileTwo.path: .updated,
            Fixtures.Metadata.fileThree.path: .existing(entry: entry)
        ])
    }

    @Test("allows being created with new files")
    func createWithChanges() {
        let created = FilesystemMetadata(changes: [
            Fixtures.Metadata.fileOne.path,
            Fixtures.Metadata.fileTwo.path
        ])
        #expect(created == FilesystemMetadata(entities: [
            Fixtures.Metadata.fileOne.path: .new,
            Fixtures.Metadata.fileTwo.path: .new
        ]))
    }

    @Test("allows being updated with new files")
    func updateWithChanges() {
        let newEntry = UUID()
        let newFile = "/tmp/file/five"

        let updated = makeFilesystemMetadata().updated(
            changes: [Fixtures.Metadata.fileOne.path, newFile],
            latestEntry: newEntry
        )

        #expect(updated == FilesystemMetadata(entities: [
            Fixtures.Metadata.fileOne.path: .updated,
            Fixtures.Metadata.fileTwo.path: .existing(entry: newEntry),
            Fixtures.Metadata.fileThree.path: .existing(entry: entry),
            newFile: .new
        ]))

        let latestEntry = UUID()
        let twice = updated.updated(changes: [], latestEntry: latestEntry)
        #expect(twice == FilesystemMetadata(entities: [
            Fixtures.Metadata.fileOne.path: .existing(entry: latestEntry),
            Fixtures.Metadata.fileTwo.path: .existing(entry: newEntry),
            Fixtures.Metadata.fileThree.path: .existing(entry: entry),
            newFile: .existing(entry: latestEntry)
        ]))
    }

    @Test("supports metadata collection")
    func metadataCollect() {
        let collected = makeFilesystemMetadata().collect { path, state in
            state == .updated ? path : nil
        }
        #expect(collected == ["/tmp/file/two"])
    }

    @Test("supports metadata retrieval")
    func metadataGet() {
        let metadata = makeFilesystemMetadata()
        #expect(metadata.get("/tmp/file/one") == .new)
        #expect(metadata.get("/tmp/file/two") == .updated)
        #expect(metadata.get("/tmp/file/other") == nil)
    }

    @Test("supports metadata search")
    func metadataSearch() throws {
        let regex = try NSRegularExpression(pattern: ".*(two|four)$")
        let result = makeFilesystemMetadata().search(regex)
        #expect(result.count == 2)
        #expect(result["/tmp/file/two"] == .updated)
        #expect(result["/tmp/file/four"] == .existing(entry: entry))
    }

    @Test("provides an empty value")
    func emptyMetadata() {
        let empty = FilesystemMetadata.empty()
        #expect(empty == FilesystemMetadata(entities: [:]))
    }
}

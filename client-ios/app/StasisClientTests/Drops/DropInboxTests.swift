import Foundation
@testable import StasisClient
import Testing

@Suite("DropInbox")
struct DropInboxTests {
    @Test("stores a file and lists it back with its content")
    func storesFile() async throws {
        let inbox = makeInbox()
        let source = try writeTemp(name: "test.txt", content: "test a")

        let metadata = try await inbox.store(filename: "test.txt", typeIdentifier: "public.plain-text", from: source)

        #expect(metadata.filename == "test.txt")
        #expect(metadata.size == 6)
        let listed = inbox.list()
        #expect(listed == [metadata])
        #expect(try Data(contentsOf: inbox.contentURL(for: metadata)) == Data("test a".utf8))
    }

    @Test("stores raw data")
    func storesData() async throws {
        let inbox = makeInbox()

        let metadata = try await inbox.store(
            filename: "test.txt",
            typeIdentifier: "public.plain-text",
            data: Data("test".utf8)
        )

        #expect(metadata.size == 4)
        #expect(try Data(contentsOf: inbox.contentURL(for: metadata)) == Data("test".utf8))
    }

    @Test("sanitizes path separators out of the stored filename")
    func sanitizesFilename() async throws {
        let inbox = makeInbox()

        let metadata = try await inbox.store(
            filename: "test/a:b.txt",
            typeIdentifier: nil,
            data: Data("test".utf8)
        )

        #expect(metadata.filename == "a_b.txt")
    }

    @Test("resolves the content url from a library path")
    func resolvesContentPath() async throws {
        let inbox = makeInbox()
        let metadata = try await inbox.store(filename: "test.txt", typeIdentifier: nil, data: Data("test".utf8))

        let byPath = inbox.contentURL(forPath: "/\(metadata.id)/\(metadata.filename)")
        #expect(byPath == inbox.contentURL(for: metadata))
    }

    @Test("lists drops ordered by creation date")
    func listsInOrder() throws {
        let inbox = makeInbox()
        let earlier = try fixture(inbox: inbox, id: "a", createdAt: Date(timeIntervalSince1970: 1))
        let later = try fixture(inbox: inbox, id: "b", createdAt: Date(timeIntervalSince1970: 2))

        #expect(inbox.list() == [earlier, later])
    }

    @Test("removes a stored drop")
    func removesDrop() async throws {
        let inbox = makeInbox()
        let metadata = try await inbox.store(filename: "test.txt", typeIdentifier: nil, data: Data("test".utf8))

        try inbox.remove(id: metadata.id)
        #expect(inbox.list().isEmpty)
    }

    private func makeInbox() -> DropInbox {
        DropInbox(directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
    }

    private func writeTemp(name: String, content: String) throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent(name)
        try Data(content.utf8).write(to: url)
        return url
    }

    private func fixture(inbox: DropInbox, id: String, createdAt: Date) throws -> DropMetadata {
        let folder = inbox.directory.appendingPathComponent(id, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        try Data("test".utf8).write(to: folder.appendingPathComponent("test.txt"))
        let metadata = DropMetadata(
            id: id,
            filename: "test.txt",
            size: 4,
            typeIdentifier: nil,
            createdAt: createdAt
        )
        try metadata.encoded().write(to: folder.appendingPathComponent(DropInbox.metadataFileName))
        return metadata
    }
}

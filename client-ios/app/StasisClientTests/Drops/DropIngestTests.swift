import Foundation
@testable import StasisClient
import Testing

@MainActor
@Suite("DropIngest")
struct DropIngestTests {
    @Test("ingests a shared file into the inbox")
    func ingestsFile() async throws {
        let inbox = makeInbox()
        let source = try writeTemp(name: "test.txt", content: "test a")
        let provider = try #require(NSItemProvider(contentsOf: source))

        let outcome = await DropIngest.ingest(items: [provider], into: inbox)

        #expect(outcome.failures.isEmpty)
        #expect(outcome.stored.count == 1)
        let stored = try #require(inbox.list().first)
        #expect(try Data(contentsOf: inbox.contentURL(for: stored)) == Data("test a".utf8))
    }

    @Test("ingests a shared web link as a url file")
    func ingestsURL() async throws {
        let inbox = makeInbox()
        let link = try #require(URL(string: "https://example.test/test"))
        let provider = NSItemProvider(object: link as NSURL)

        let outcome = await DropIngest.ingest(items: [provider], into: inbox)

        let stored = try #require(outcome.stored.first)
        #expect(stored.filename.hasSuffix(".url"))
        #expect(try Data(contentsOf: inbox.contentURL(for: stored)) == Data(link.absoluteString.utf8))
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
}

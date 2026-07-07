import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Synchronization
import Testing

@Suite("DestagedByteStringSource")
struct DestagedByteStringSourceTests {
    private func makeProviders(staging: any FileStaging) -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.md5,
            staging: staging,
            compression: MockCompression(),
            decryptor: MockDecrypting(),
            clients: StaticClients(
                api: MockServerApiEndpointClient(),
                core: MockServerCoreEndpointClient()
            ),
            track: MockRecoveryTracker(),
            analytics: NoOpAnalyticsCollector(),
            kinds: [RecoveryEntityKinds.filesystem]
        )
    }

    @Test("writes the source through staging to the target path")
    func destagesSuccessfully() async throws {
        let staging = ContentObservingStaging()
        let providers = makeProviders(staging: staging)

        try await DestagedByteStringSource.destage(
            makeDataStream(Data("original".utf8)),
            to: URL(fileURLWithPath: "/tmp/file/one"),
            providers: providers
        )

        let stats = staging.statistics
        #expect(stats.temporaryCreated == 1)
        #expect(stats.temporaryDiscarded == 0)
        #expect(stats.destaged == 1)
        #expect(staging.lastStagedContents == Data("original".utf8))
        #expect(staging.lastDestageTarget?.path == "/tmp/file/one")

        for path in staging.createdPaths { try? FileManager.default.removeItem(at: path) }
    }

    @Test("discards the temporary file when the source throws")
    func discardsOnSourceFailure() async {
        let staging = ContentObservingStaging()
        let providers = makeProviders(staging: staging)

        let failingStream = makeDataStream(
            [Data("partial".utf8)],
            thenThrow: TestFailure(message: "stream broken")
        )

        await #expect(throws: TestFailure(message: "stream broken")) {
            try await DestagedByteStringSource.destage(
                failingStream,
                to: URL(fileURLWithPath: "/tmp/file/one"),
                providers: providers
            )
        }

        let stats = staging.statistics
        #expect(stats.temporaryCreated == 1)
        #expect(stats.temporaryDiscarded == 1)
        #expect(stats.destaged == 0)
    }

    @Test("discards the temporary file when destage fails")
    func discardsOnDestageFailure() async {
        let staging = FailingDestageStaging()
        let providers = makeProviders(staging: staging)

        await #expect(throws: DestageFailure.self) {
            try await DestagedByteStringSource.destage(
                makeDataStream(Data("original".utf8)),
                to: URL(fileURLWithPath: "/tmp/file/one"),
                providers: providers
            )
        }

        let stats = staging.statistics
        #expect(stats.temporaryCreated == 1)
        #expect(stats.temporaryDiscarded == 1)
        #expect(stats.destaged == 0)
    }
}

private struct DestageFailure: Error, Equatable {}

private final class FailingDestageStaging: FileStaging, Sendable {
    private let recorded = Mutex<MockFileStaging.Stats>(MockFileStaging.Stats())
    var statistics: MockFileStaging.Stats { recorded.withLock { $0 } }

    func temporary() async throws -> URL {
        recorded.withLock { $0.temporaryCreated += 1 }
        return URL(fileURLWithPath: "/tmp/\(UUID().uuidString)")
    }

    func discard(file: URL) async throws {
        recorded.withLock { $0.temporaryDiscarded += 1 }
    }

    func destage(from source: URL, to target: URL) async throws {
        throw DestageFailure()
    }
}

private final class ContentObservingStaging: FileStaging, Sendable {
    private let state = Mutex<State>(State())

    struct State: Sendable {
        var stats = MockFileStaging.Stats()
        var createdPaths: [URL] = []
        var lastStagedContents: Data?
        var lastDestageTarget: URL?
    }

    var statistics: MockFileStaging.Stats { state.withLock { $0.stats } }
    var createdPaths: [URL] { state.withLock { $0.createdPaths } }
    var lastStagedContents: Data? { state.withLock { $0.lastStagedContents } }
    var lastDestageTarget: URL? { state.withLock { $0.lastDestageTarget } }

    func temporary() async throws -> URL {
        let url = URL(fileURLWithPath: "/tmp/\(UUID().uuidString)")
        state.withLock {
            $0.stats.temporaryCreated += 1
            $0.createdPaths.append(url)
        }
        return url
    }

    func discard(file: URL) async throws {
        state.withLock { $0.stats.temporaryDiscarded += 1 }
        try? FileManager.default.removeItem(at: file)
    }

    func destage(from source: URL, to target: URL) async throws {
        let contents = (try? Data(contentsOf: source)) ?? Data()
        state.withLock {
            $0.stats.destaged += 1
            $0.lastStagedContents = contents
            $0.lastDestageTarget = target
        }
        try? FileManager.default.removeItem(at: source)
    }
}

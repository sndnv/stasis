import Foundation
@testable import StasisClientLib
import Synchronization

final class MockFileStaging: FileStaging, Sendable {
    private let recorded = Mutex<Stats>(Stats())

    struct Stats: Sendable {
        var temporaryCreated: Int = 0
        var temporaryDiscarded: Int = 0
        var destaged: Int = 0
    }

    var statistics: Stats { recorded.withLock { $0 } }

    func temporary() async throws -> URL {
        recorded.withLock { $0.temporaryCreated += 1 }
        return URL(fileURLWithPath: "/tmp/\(UUID().uuidString)")
    }

    func discard(file: URL) async throws {
        recorded.withLock { $0.temporaryDiscarded += 1 }
    }

    func destage(from source: URL, to target: URL) async throws {
        recorded.withLock { $0.destaged += 1 }
    }
}

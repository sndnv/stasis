import Foundation

public protocol FileStaging: Sendable {
    func temporary() async throws -> URL
    func discard(file: URL) async throws
    func destage(from source: URL, to target: URL) async throws
}

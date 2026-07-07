import Foundation

public protocol EntityContentMetadata: Sendable {
    var size: Int64 { get }
    var checksum: Data { get }
    var crates: [String: UUID] { get }
    var compression: String { get }
}

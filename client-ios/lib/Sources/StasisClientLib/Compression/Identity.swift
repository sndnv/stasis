import Foundation

public struct Identity: Compressor {
    public static let shared = Identity()

    public let name = "none"

    private init() {}

    public func compress(_ data: Data) throws -> Data { data }
    public func decompress(_ data: Data) throws -> Data { data }
}

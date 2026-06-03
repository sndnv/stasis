import Foundation

public struct RecoveryCrate: Sendable {
    public typealias Source = @Sendable () async throws -> AsyncThrowingStream<Data, Error>

    public let partId: Int
    public let partPath: String
    public let source: Source

    public init(partId: Int, partPath: String, source: @escaping Source) {
        self.partId = partId
        self.partPath = partPath
        self.source = source
    }
}

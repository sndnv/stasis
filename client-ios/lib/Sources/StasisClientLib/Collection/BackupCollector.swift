import Foundation

public protocol BackupCollector: Sendable {
    func collect() -> AsyncThrowingStream<SourceEntity, Error>
}

import Foundation

public protocol RecoveryCollector: Sendable {
    func collect() -> AsyncThrowingStream<TargetEntity, Error>
}

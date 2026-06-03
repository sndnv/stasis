import Foundation
@testable import StasisClientLib

struct MockRecoveryCollector: RecoveryCollector {
    let files: [TargetEntity]

    func collect() -> AsyncThrowingStream<TargetEntity, Error> {
        AsyncThrowingStream { continuation in
            for file in files { continuation.yield(file) }
            continuation.finish()
        }
    }
}

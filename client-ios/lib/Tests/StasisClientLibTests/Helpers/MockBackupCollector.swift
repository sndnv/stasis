import Foundation
@testable import StasisClientLib

struct MockBackupCollector: BackupCollector {
    let files: [SourceEntity]

    func collect() -> AsyncThrowingStream<SourceEntity, Error> {
        AsyncThrowingStream { continuation in
            for file in files {
                continuation.yield(file)
            }
            continuation.finish()
        }
    }
}

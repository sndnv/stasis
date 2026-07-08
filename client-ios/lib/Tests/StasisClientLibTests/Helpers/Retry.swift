import Foundation
import Testing

struct RetryableTestFailure: Error {
    let message: String
    let sourceLocation: SourceLocation
}

func withRetry(
    _ times: Int = 2,
    _ operation: () async throws -> Void
) async throws {
    var remaining = times
    while true {
        do {
            try await operation()
            return
        } catch let failure as RetryableTestFailure {
            guard remaining > 0 else {
                Issue.record(Comment(rawValue: failure.message), sourceLocation: failure.sourceLocation)
                return
            }
            remaining -= 1
        }
    }
}

func expectRetryable(
    _ condition: Bool,
    _ message: @autoclosure () -> String,
    sourceLocation: SourceLocation = #_sourceLocation
) throws {
    guard condition else {
        throw RetryableTestFailure(message: message(), sourceLocation: sourceLocation)
    }
}

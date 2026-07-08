import Foundation
import Testing

@Suite("WithRetry")
struct WithRetryTests {
    @Test("runs the operation once when it passes")
    func runsOnceOnSuccess() async throws {
        let attempts = Counter()
        try await withRetry {
            attempts.increment()
            try expectRetryable(true, "ok")
        }
        #expect(attempts.value == 1)
    }

    @Test("retries until the operation succeeds")
    func retriesUntilSuccess() async throws {
        let attempts = Counter()
        try await withRetry(2) {
            attempts.increment()
            try expectRetryable(attempts.value >= 2, "need at least two attempts")
        }
        #expect(attempts.value == 2)
    }

    @Test("records a failure after exhausting retries")
    func recordsAfterExhausting() async throws {
        let attempts = Counter()
        await withKnownIssue {
            try await withRetry(2) {
                attempts.increment()
                try expectRetryable(false, "always fails")
            }
        }
        #expect(attempts.value == 3)
    }

    @Test("does not retry non-retryable errors")
    func doesNotRetryOtherErrors() async {
        let attempts = Counter()
        await #expect(throws: TestFailure.self) {
            try await withRetry(2) {
                attempts.increment()
                throw TestFailure()
            }
        }
        #expect(attempts.value == 1)
    }

    @Test("expectRetryable throws only on a false condition")
    func expectRetryableThrows() throws {
        #expect(throws: RetryableTestFailure.self) {
            try expectRetryable(false, "boom")
        }
        try expectRetryable(true, "fine")
    }
}

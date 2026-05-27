import Foundation
@testable import StasisClientLib
import Testing

@Suite("Retry")
struct RetryTests {
    private let url = URL(string: "http://localhost/test")!

    private func response(_ status: Int) -> HTTPURLResponse {
        HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
    }

    @Test("returns immediately on first non-retryable success")
    func returnsImmediatelyOn2xx() async throws {
        let calls = Calls()
        let config = RetryConfig(
            minBackoff: .milliseconds(1),
            maxBackoff: .milliseconds(2),
            randomFactor: 0.1,
            maxRetries: 5
        )

        let (_, response) = try await Retry.send(config: config) {
            await calls.bump()
            return (Data("ok".utf8), self.response(200))
        }

        #expect(response.statusCode == 200)
        await #expect(calls.count == 1)
    }

    @Test("retries retryable responses then returns the successful one")
    func retriesRetryable() async throws {
        let statuses = StatusSequence([408, 425, 429, 500, 502, 503, 200])
        let config = RetryConfig(
            minBackoff: .milliseconds(1),
            maxBackoff: .milliseconds(2),
            randomFactor: 0.1,
            maxRetries: 6
        )

        let (_, response) = try await Retry.send(config: config) {
            let status = await statuses.next()
            return (Data(), self.response(status))
        }

        #expect(response.statusCode == 200)
        await #expect(statuses.consumed == 7)
    }

    @Test("stops retrying after maxRetries even if still retryable")
    func stopsAfterMaxRetries() async throws {
        let statuses = StatusSequence([503, 503, 503, 503, 503])
        let config = RetryConfig(
            minBackoff: .milliseconds(1),
            maxBackoff: .milliseconds(2),
            randomFactor: 0.1,
            maxRetries: 2
        )

        let (_, response) = try await Retry.send(config: config) {
            let status = await statuses.next()
            return (Data(), self.response(status))
        }

        #expect(response.statusCode == 503)
        await #expect(statuses.consumed == 3)
    }

    @Test("canRetry classifies status codes correctly")
    func canRetryClassification() {
        #expect(!Retry.canRetry(status: 100))

        #expect(!Retry.canRetry(status: 200))
        #expect(!Retry.canRetry(status: 201))
        #expect(!Retry.canRetry(status: 202))
        #expect(!Retry.canRetry(status: 204))

        #expect(!Retry.canRetry(status: 302))
        #expect(!Retry.canRetry(status: 307))
        #expect(!Retry.canRetry(status: 308))

        #expect(!Retry.canRetry(status: 400))
        #expect(!Retry.canRetry(status: 401))
        #expect(!Retry.canRetry(status: 403))
        #expect(!Retry.canRetry(status: 404))
        #expect(!Retry.canRetry(status: 405))
        #expect(!Retry.canRetry(status: 406))
        #expect(Retry.canRetry(status: 408))
        #expect(Retry.canRetry(status: 424))
        #expect(Retry.canRetry(status: 425))
        #expect(Retry.canRetry(status: 429))

        #expect(Retry.canRetry(status: 500))
        #expect(!Retry.canRetry(status: 501))
        #expect(Retry.canRetry(status: 502))
        #expect(Retry.canRetry(status: 503))
        #expect(Retry.canRetry(status: 504))
        #expect(Retry.canRetry(status: 509))
        #expect(Retry.canRetry(status: 598))
        #expect(Retry.canRetry(status: 599))
    }
}

private actor Calls {
    private(set) var count = 0
    func bump() { count += 1 }
}

private actor StatusSequence {
    private var statuses: [Int]
    private(set) var consumed = 0

    init(_ statuses: [Int]) { self.statuses = statuses }

    func next() -> Int {
        consumed += 1
        return statuses.isEmpty ? 200 : statuses.removeFirst()
    }
}

import Foundation

public struct RetryConfig: Sendable, Equatable {
    public let minBackoff: Duration
    public let maxBackoff: Duration
    public let randomFactor: Double
    public let maxRetries: Int

    public init(minBackoff: Duration, maxBackoff: Duration, randomFactor: Double, maxRetries: Int) {
        self.minBackoff = minBackoff
        self.maxBackoff = maxBackoff
        self.randomFactor = randomFactor
        self.maxRetries = maxRetries
    }

    public static let `default` = RetryConfig(
        minBackoff: .milliseconds(500),
        maxBackoff: .seconds(3),
        randomFactor: 0.1,
        maxRetries: 5
    )

    public static let disabled = RetryConfig(
        minBackoff: .zero,
        maxBackoff: .zero,
        randomFactor: 0.0,
        maxRetries: 0
    )
}

public enum Retry {
    public static func send(
        config: RetryConfig,
        body: () async throws -> (Data, HTTPURLResponse)
    ) async throws -> (Data, HTTPURLResponse) {
        var (data, response) = try await body()
        var currentMs = config.minBackoff.inMilliseconds
        let maxMs = config.maxBackoff.inMilliseconds
        for _ in 0..<config.maxRetries {
            if !canRetry(status: response.statusCode) {
                return (data, response)
            }
            currentMs = min(Int64(Double(currentMs) * (1 + config.randomFactor)), maxMs)
            try await Task.sleep(for: .milliseconds(currentMs))
            (data, response) = try await body()
        }
        return (data, response)
    }

    public static func canRetry(status: Int) -> Bool {
        switch status {
        case 408, 424, 425, 429: true
        case 500, 502, 503, 504, 509, 598, 599: true
        default: false
        }
    }
}

private extension Duration {
    var inMilliseconds: Int64 {
        let (seconds, attoseconds) = components
        return seconds * 1_000 + attoseconds / 1_000_000_000_000_000
    }
}

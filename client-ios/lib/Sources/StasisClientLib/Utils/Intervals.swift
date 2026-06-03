import Foundation

enum Intervals {
    static func fuzzy(_ interval: TimeInterval) -> TimeInterval {
        let low = interval - (interval * 0.02)
        let high = interval + (interval * 0.03)
        return Double.random(in: low..<high)
    }

    static func nanoseconds(_ seconds: TimeInterval) -> UInt64 {
        UInt64(max(seconds, 0) * 1_000_000_000)
    }
}

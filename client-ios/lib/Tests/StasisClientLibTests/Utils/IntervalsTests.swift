import Foundation
@testable import StasisClientLib
import Testing

@Suite("Intervals")
struct IntervalsTests {
    @Test("nanoseconds converts seconds to UInt64")
    func nanosecondsConversion() {
        #expect(Intervals.nanoseconds(0) == 0)
        #expect(Intervals.nanoseconds(1) == 1_000_000_000)
        #expect(Intervals.nanoseconds(0.001) == 1_000_000)
        #expect(Intervals.nanoseconds(2.5) == 2_500_000_000)
    }

    @Test("nanoseconds clamps negative values to zero")
    func nanosecondsClampsNegative() {
        #expect(Intervals.nanoseconds(-1) == 0)
        #expect(Intervals.nanoseconds(-0.001) == 0)
    }

    @Test("fuzzy returns a value within the ±2/3% jitter range")
    func fuzzyInRange() {
        let base: TimeInterval = 100
        let low = base - (base * 0.02)
        let high = base + (base * 0.03)
        for _ in 0..<1_000 {
            let value = Intervals.fuzzy(base)
            #expect(value >= low)
            #expect(value < high)
        }
    }

    @Test("fuzzy produces variation across calls")
    func fuzzyVaries() {
        let base: TimeInterval = 100
        let samples = (0..<10).map { _ in Intervals.fuzzy(base) }
        #expect(Set(samples).count > 1)
    }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("Date.epochMillis")
struct DateEpochMillisTests {
    @Test("round-trips epoch millis")
    func roundTripsEpochMillis() {
        let millis: UInt64 = 1_716_000_000_123
        let date = Date(epochMillis: millis)
        #expect(date.epochMillis == millis)
    }

    @Test("encodes the unix epoch as 0")
    func encodesUnixEpochAsZero() {
        #expect(Date(timeIntervalSince1970: 0).epochMillis == 0)
    }

    @Test("decodes 0 as the unix epoch")
    func decodesZeroAsUnixEpoch() {
        #expect(Date(epochMillis: 0) == Date(timeIntervalSince1970: 0))
    }

    @Test("preserves sub-second precision to the millisecond")
    func preservesMillisecondPrecision() {
        let date = Date(timeIntervalSince1970: 1_716_000_000.456)
        #expect(date.epochMillis == 1_716_000_000_456)
    }
}

@Suite("Dictionary URL ↔ path keyed")
struct DictionaryKeyConversionsTests {
    @Test("keyedByPath converts URL keys to path strings")
    func keyedByPathConvertsKeys() {
        let urls: [URL: Int] = [
            URL(fileURLWithPath: "/tmp/a"): 1,
            URL(fileURLWithPath: "/tmp/b"): 2
        ]
        let paths = urls.keyedByPath()
        #expect(paths == ["/tmp/a": 1, "/tmp/b": 2])
    }

    @Test("keyedByFileURL converts path strings to file URL keys")
    func keyedByFileURLConvertsKeys() {
        let paths: [String: Int] = ["/tmp/a": 1, "/tmp/b": 2]
        let urls = paths.keyedByFileURL()
        #expect(urls == [
            URL(fileURLWithPath: "/tmp/a"): 1,
            URL(fileURLWithPath: "/tmp/b"): 2
        ])
    }

    @Test("round-trips URL ↔ path keys via both helpers")
    func roundTripsViaBothHelpers() {
        let original: [URL: String] = [
            URL(fileURLWithPath: "/tmp/one"): "a",
            URL(fileURLWithPath: "/tmp/two"): "b"
        ]
        let viaPath = original.keyedByPath()
        let restored = viaPath.keyedByFileURL()
        #expect(restored == original)
    }

    @Test("handles empty dictionaries in both directions")
    func handlesEmptyDictionaries() {
        let emptyUrls: [URL: Int] = [:]
        let emptyPaths: [String: Int] = [:]
        #expect(emptyUrls.keyedByPath().isEmpty)
        #expect(emptyPaths.keyedByFileURL().isEmpty)
    }
}

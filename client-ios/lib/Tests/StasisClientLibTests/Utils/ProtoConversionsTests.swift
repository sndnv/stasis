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

@Suite("Dictionary EntityRef ↔ key")
struct DictionaryKeyConversionsTests {
    @Test("keyedByKey converts EntityRef keys to key strings")
    func keyedByKeyConvertsKeys() {
        let refs: [EntityRef: Int] = [
            .filesystem(URL(fileURLWithPath: "/tmp/a")): 1,
            .library(scheme: "photos", path: "/album"): 2
        ]
        let keys = refs.keyedByKey()
        #expect(keys == ["/tmp/a": 1, "photos:/album": 2])
    }

    @Test("keyedByRef converts key strings to EntityRef keys")
    func keyedByRefConvertsKeys() {
        let keys: [String: Int] = ["/tmp/a": 1, "photos:/album": 2]
        let refs = keys.keyedByRef()
        #expect(refs == [
            .filesystem(URL(fileURLWithPath: "/tmp/a")): 1,
            .library(scheme: "photos", path: "/album"): 2
        ])
    }

    @Test("round-trips EntityRef ↔ key via both helpers")
    func roundTripsViaBothHelpers() {
        let original: [EntityRef: String] = [
            .filesystem(URL(fileURLWithPath: "/tmp/one")): "a",
            .library(scheme: "photos", path: "/two"): "b"
        ]
        let viaKey = original.keyedByKey()
        let restored = viaKey.keyedByRef()
        #expect(restored == original)
    }

    @Test("handles empty dictionaries in both directions")
    func handlesEmptyDictionaries() {
        let emptyRefs: [EntityRef: Int] = [:]
        let emptyKeys: [String: Int] = [:]
        #expect(emptyRefs.keyedByKey().isEmpty)
        #expect(emptyKeys.keyedByRef().isEmpty)
    }
}

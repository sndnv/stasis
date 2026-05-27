import Foundation
@testable import StasisClientLib
import Testing

@Suite("LocalDateTime")
struct LocalDateTimeTests {
    @Test("parses form without seconds (HH:mm)")
    func parsesWithoutSeconds() {
        let parsed = LocalDateTime("2024-01-15T22:00")
        #expect(parsed.components.year == 2024)
        #expect(parsed.components.month == 1)
        #expect(parsed.components.day == 15)
        #expect(parsed.components.hour == 22)
        #expect(parsed.components.minute == 0)
        #expect(parsed.components.second == 0)
        #expect(parsed.components.nanosecond == nil)
    }

    @Test("parses form with seconds (HH:mm:ss)")
    func parsesWithSeconds() {
        let parsed = LocalDateTime("2024-01-15T22:00:30")
        #expect(parsed.components.year == 2024)
        #expect(parsed.components.month == 1)
        #expect(parsed.components.day == 15)
        #expect(parsed.components.hour == 22)
        #expect(parsed.components.minute == 0)
        #expect(parsed.components.second == 30)
        #expect(parsed.components.nanosecond == nil)
    }

    @Test("parses form with millisecond fraction (HH:mm:ss.SSS)")
    func parsesWithMillisecondFraction() {
        let parsed = LocalDateTime("2024-01-15T22:00:30.123")
        #expect(parsed.components.year == 2024)
        #expect(parsed.components.month == 1)
        #expect(parsed.components.day == 15)
        #expect(parsed.components.hour == 22)
        #expect(parsed.components.minute == 0)
        #expect(parsed.components.second == 30)
        #expect(parsed.components.nanosecond == 123_000_000)
    }

    @Test("parses form with microsecond fraction (HH:mm:ss.SSSSSS)")
    func parsesWithMicrosecondFraction() {
        let parsed = LocalDateTime("2024-01-15T22:00:30.123456")
        #expect(parsed.components.second == 30)
        #expect(parsed.components.nanosecond == 123_456_000)
    }

    @Test("parses form with nanosecond fraction (HH:mm:ss.SSSSSSSSS)")
    func parsesWithNanosecondFraction() {
        let parsed = LocalDateTime("2024-01-15T22:00:30.123456789")
        #expect(parsed.components.second == 30)
        #expect(parsed.components.nanosecond == 123_456_789)
    }

    @Test("returns empty components on garbage input")
    func emptyComponentsOnGarbage() {
        let parsed = LocalDateTime("not a date")
        #expect(parsed.components.year == nil)
        #expect(parsed.components.month == nil)
        #expect(parsed.components.day == nil)
        #expect(parsed.components.hour == nil)
        #expect(parsed.components.minute == nil)
        #expect(parsed.components.second == nil)
        #expect(parsed.components.nanosecond == nil)
    }

    @Test("returns empty components on missing T separator")
    func emptyComponentsWithoutT() {
        let parsed = LocalDateTime("2024-01-15 22:00:30")
        #expect(parsed.components.year == nil)
    }

    @Test("returns empty components on missing date parts")
    func emptyComponentsOnPartialDate() {
        let parsed = LocalDateTime("2024-01T22:00:30")
        #expect(parsed.components.year == nil)
    }

    @Test("returns nil nanosecond when fractional digits exceed 9")
    func dropsExcessivelyLongFraction() {
        let parsed = LocalDateTime("2024-01-15T22:00:30.1234567890")
        #expect(parsed.components.second == 30)
        #expect(parsed.components.nanosecond == nil)
    }

    @Test("returns nil nanosecond when fraction is non-numeric")
    func dropsNonNumericFraction() {
        let parsed = LocalDateTime("2024-01-15T22:00:30.abc")
        #expect(parsed.components.second == 30)
        #expect(parsed.components.nanosecond == nil)
    }

    @Test("preserves the wire string verbatim on construction")
    func preservesWireString() {
        let raw = "2024-01-15T22:00"
        #expect(LocalDateTime(raw).value == raw)
    }

    @Test("encodes to a JSON string, not an object")
    func encodesAsJsonString() throws {
        let local = LocalDateTime("2024-01-15T22:00:30.123")
        let data = try JSONEncoder().encode(local)
        let json = try #require(String(data: data, encoding: .utf8))
        #expect(json == "\"2024-01-15T22:00:30.123\"")
    }

    @Test("decodes from a JSON string")
    func decodesFromJsonString() throws {
        let json = Data("\"2024-01-15T22:00:30.123\"".utf8)
        let decoded = try JSONDecoder().decode(LocalDateTime.self, from: json)
        #expect(decoded.value == "2024-01-15T22:00:30.123")
        #expect(decoded.components.second == 30)
        #expect(decoded.components.nanosecond == 123_000_000)
    }

    @Test("round-trips through JSON for every form")
    func roundtripsAllForms() throws {
        let forms = [
            "2024-01-15T22:00",
            "2024-01-15T22:00:30",
            "2024-01-15T22:00:30.123",
            "2024-01-15T22:00:30.123456",
            "2024-01-15T22:00:30.123456789",
        ]
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()
        for raw in forms {
            let original = LocalDateTime(raw)
            let data = try encoder.encode(original)
            let decoded = try decoder.decode(LocalDateTime.self, from: data)
            #expect(decoded == original)
            #expect(decoded.value == raw)
        }
    }

    @Test("equates by wire string value")
    func equatesByWireString() {
        let one = LocalDateTime("2024-01-15T22:00")
        let two = LocalDateTime("2024-01-15T22:00")
        let three = LocalDateTime("2024-01-15T22:00:00")
        #expect(one == two)
        #expect(one != three)
    }

    @Test("hashes consistently with equality")
    func hashesConsistently() {
        let set: Set<LocalDateTime> = [
            LocalDateTime("2024-01-15T22:00"),
            LocalDateTime("2024-01-15T22:00"),
            LocalDateTime("2024-01-15T23:00"),
        ]
        #expect(set.count == 2)
    }
}

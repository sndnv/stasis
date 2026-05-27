import Foundation
@testable import StasisClientLib
import Testing

@Suite("SecondsDuration")
struct SecondsDurationTests {
    private let encoder = JSONCoders.encoder()
    private let decoder = JSONCoders.decoder()

    @Test("encodes as a JSON number")
    func encodes() throws {
        let value = SecondsDuration(42 * 60)
        let encoded = try encoder.encode(value)
        #expect(String(data: encoded, encoding: .utf8) == "2520")
    }

    @Test("decodes from a JSON number")
    func decodes() throws {
        let decoded = try decoder.decode(SecondsDuration.self, from: Data("2520".utf8))
        #expect(decoded == SecondsDuration(42 * 60))
    }

    @Test("exposes a Swift Duration")
    func exposesDuration() {
        #expect(SecondsDuration(5).duration == .seconds(5))
        #expect(SecondsDuration(0).duration == .seconds(0))
    }
}

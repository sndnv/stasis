import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("StatusFormatters")
struct StatusFormattersTests {
    @Test("shortId takes the lowercased first eight characters")
    func shortId() {
        let id = UUID(uuidString: "AABBCCDD-1122-3344-5566-778899AABBCC")!
        #expect(StatusFormatters.shortId(id) == "aabbccdd")
    }

    @Test("bytes renders using file-size units")
    func bytes() {
        #expect(!StatusFormatters.bytes(0).isEmpty)
        #expect(StatusFormatters.bytes(1_500).contains("KB"))
        #expect(StatusFormatters.bytes(5_000_000).contains("MB"))
    }

    @Test("duration renders abbreviated units")
    func duration() {
        #expect(StatusFormatters.duration(SecondsDuration(45)).contains("sec"))
        #expect(StatusFormatters.duration(SecondsDuration(90)).contains("min"))
        #expect(StatusFormatters.duration(SecondsDuration(7_200)).contains("hr"))
        #expect(StatusFormatters.duration(SecondsDuration(172_800)).contains("day"))
    }
}

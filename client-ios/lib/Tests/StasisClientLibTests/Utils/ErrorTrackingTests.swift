import Foundation
@testable import StasisClientLib
import Testing

@Suite("Error.tracked")
struct ErrorTrackingTests {
    private struct SampleFailure: LocalizedError {
        let reason: String
        var errorDescription: String? { reason }
    }

    private enum TaggedFailure: LocalizedError {
        case missing(String)
        var errorDescription: String? {
            switch self {
            case .missing(let name): "missing \(name)"
            }
        }
    }

    @Test("formats struct error as <Type> - <localizedDescription>")
    func formatsStructError() {
        let error = SampleFailure(reason: "boom")
        #expect(error.tracked == "SampleFailure - boom")
    }

    @Test("formats enum error using its localizedDescription")
    func formatsEnumError() {
        let error = TaggedFailure.missing("key")
        #expect(error.tracked == "TaggedFailure - missing key")
    }
}

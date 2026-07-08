import Foundation
@testable import StasisClient
import Testing

@Suite("LibraryContentUnavailable")
struct LibraryContentUnavailableTests {
    @Test("describes the missing content key")
    func message() {
        let error = LibraryContentUnavailable(key: "contacts:/test")
        #expect(error.errorDescription == "No backed-up content is available for [contacts:/test]")
    }
}

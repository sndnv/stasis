import Foundation
@testable import StasisClient
import Testing

@Suite("LibraryPermissionMissing")
struct LibraryPermissionMissingTests {
    @Test("describes the required scheme")
    func message() {
        let error = LibraryPermissionMissing(scheme: "contacts")
        #expect(error.errorDescription == "Permission required to access the [contacts] library")
    }
}

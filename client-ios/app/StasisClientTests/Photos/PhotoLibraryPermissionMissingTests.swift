import Foundation
@testable import StasisClient
import Testing

@Suite("PhotoLibraryPermissionMissing")
struct PhotoLibraryPermissionMissingTests {
    @Test("describes the photo-library permission requirement")
    func message() {
        let error = PhotoLibraryPermissionMissing(scheme: "photos")
        #expect(error.errorDescription == "Permission required to access the photo library")
    }
}

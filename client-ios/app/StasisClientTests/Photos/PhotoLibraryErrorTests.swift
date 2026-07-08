import Foundation
@testable import StasisClient
import Testing

@Suite("PhotoLibraryError")
struct PhotoLibraryErrorTests {
    @Test("describes each case")
    func messages() {
        let content = PhotoLibraryError.contentUnavailable(key: "photos:/test").errorDescription
        let asset = PhotoLibraryError.assetUnavailable(localIdentifier: "test").errorDescription
        #expect(content == "No photo content is available for [photos:/test]")
        #expect(asset == "The photo [test] is no longer available")
        #expect(PhotoLibraryError.writeFailed.errorDescription == "Failed to write to the photo library")
    }
}

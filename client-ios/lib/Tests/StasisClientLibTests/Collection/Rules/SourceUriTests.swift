import Foundation
@testable import StasisClientLib
import Testing

@Suite("SourceUri")
struct SourceUriTests {
    @Test("extracts the scheme from a source", arguments: [
        ("/home/test", String?.none),
        ("home/test", nil),
        ("/home/test path", nil),
        ("", nil),

        ("C:\\test", nil),
        ("12:30", nil),

        ("photos:/test/file.heic", "photos"),
        ("photos://test/file.heic", "photos"),
        ("photos:test", "photos"),
        ("photos:", "photos"),
        ("contacts:/", "contacts"),

        ("x-y.z+1:/a", "x-y.z+1"),
        ("PHOTOS:/x", "PHOTOS")
    ])
    func extractsScheme(source: String, expected: String?) {
        #expect(SourceUri.scheme(source) == expected)
    }
}

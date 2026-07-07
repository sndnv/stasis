import Foundation
@testable import StasisClient
import Testing

@Suite("ExportFile")
struct ExportFileTests {
    @Test("infers image extensions from magic bytes")
    func infersImageExtensions() {
        #expect(ExportFile.inferExtension(Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])) == "png")
        #expect(ExportFile.inferExtension(Data([0xFF, 0xD8, 0xFF, 0xE0])) == "jpg")
        #expect(ExportFile.inferExtension(Data([0x25, 0x50, 0x44, 0x46])) == "pdf")
    }

    @Test("falls back to txt for utf8 and bin for binary")
    func infersTextAndBinary() {
        #expect(ExportFile.inferExtension(Data("test a".utf8)) == "txt")
        #expect(ExportFile.inferExtension(Data([0xFF, 0xFE, 0xFF])) == "bin")
    }

    @Test("sanitizes unsafe characters and empty names")
    func sanitizesNames() {
        #expect(ExportFile.sanitize("test/a b.txt") == "test_a_b_txt")
        #expect(ExportFile.sanitize("   ") == "content")
    }

    @Test("writes bytes to a temporary file named after the entity")
    func writesTemporaryFile() throws {
        let url = try ExportFile.write(name: "test a", fileExtension: "vcf", bytes: Data("test".utf8))

        #expect(url.lastPathComponent == "test_a.vcf")
        #expect(try Data(contentsOf: url) == Data("test".utf8))

        try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
    }
}

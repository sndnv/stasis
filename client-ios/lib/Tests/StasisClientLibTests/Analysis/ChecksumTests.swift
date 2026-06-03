import Foundation
@testable import StasisClientLib
import Testing

@Suite("Checksum")
struct ChecksumTests {
    private var sourceFile: URL { AnalysisResources.url("digest-source-file") }

    @Test("calculates digest checksums for files")
    func digest() async throws {
        let expected = Data(base64Encoded:
            "TBXDXSDpCXdFXq8z6A/fhbJ9fNnElgSwkzM0CG69pnBKs4DkJ2qhk+jjiAUBjhPeqdaFn7b9jA/p1liW+Cio8A==")!
        let actual = try await Checksums.digest(file: sourceFile, algorithm: "SHA-512")
        #expect(actual == expected)
    }

    @Test("calculates CRC32 checksums for files")
    func crc32() async throws {
        let expected = Data(base64Encoded: "I3uy/A==")!
        let actual = try await Checksums.crc32.calculate(file: sourceFile)
        #expect(actual == expected)
    }

    @Test("calculates MD5 checksums for files")
    func md5() async throws {
        let expected = Data(base64Encoded: "XaU/UPykvVm2Hin44sopRQ==")!
        let actual = try await Checksums.md5.calculate(file: sourceFile)
        #expect(actual == expected)
    }

    @Test("calculates SHA1 checksums for files")
    func sha1() async throws {
        let expected = Data(base64Encoded: "X5AnedsUFUmP7JgWL0AZauYhlZ0=")!
        let actual = try await Checksums.sha1.calculate(file: sourceFile)
        #expect(actual == expected)
    }

    @Test("calculates SHA256 checksums for files")
    func sha256() async throws {
        let expected = Data(base64Encoded: "ANRovTMg00Bka66VCa6N0/Jv3XcIByUaBaMU0Ke6MLFk")!
        let actual = try await Checksums.sha256.calculate(file: sourceFile)
        #expect(actual == expected)
    }

    @Test("provides checksum implementations based on config")
    func factory() throws {
        #expect(try Checksums.apply("crc32") == .crc32)
        #expect(try Checksums.apply("md5") == .md5)
        #expect(try Checksums.apply("sha1") == .sha1)
        #expect(try Checksums.apply("sha256") == .sha256)
    }

    @Test("rejects unsupported checksum names")
    func unsupported() {
        #expect(throws: ChecksumError.self) { try Checksums.apply("bogus") }
    }

    @Test("calculates SHA384 digest checksums for files")
    func sha384() async throws {
        let digest = try await Checksums.digest(file: sourceFile, algorithm: "SHA-384")
        #expect(digest.count == 48)
    }

    @Test("rejects unsupported algorithms when computing a digest")
    func unsupportedDigest() async {
        await #expect(throws: ChecksumError.self) {
            _ = try await Checksums.digest(file: sourceFile, algorithm: "bogus")
        }
    }
}

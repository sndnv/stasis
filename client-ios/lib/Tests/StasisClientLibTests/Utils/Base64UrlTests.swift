import Foundation
@testable import StasisClientLib
import Testing

@Suite("Base64Url")
struct Base64UrlTests {
    @Test("encodes empty data as an empty string")
    func encodesEmpty() {
        #expect(Data().base64UrlEncodedString() == "")
    }

    @Test("decodes empty input as empty data")
    func decodesEmpty() {
        #expect(Data(base64UrlEncoded: "") == Data())
    }

    @Test("encodes RFC 4648 vectors without padding")
    func encodesRfcVectors() {
        let vectors: [(String, String)] = [
            ("f", "Zg"),
            ("fo", "Zm8"),
            ("foo", "Zm9v"),
            ("foob", "Zm9vYg"),
            ("fooba", "Zm9vYmE"),
            ("foobar", "Zm9vYmFy")
        ]
        for (input, expected) in vectors {
            #expect(Data(input.utf8).base64UrlEncodedString() == expected)
        }
    }

    @Test("decodes RFC 4648 vectors (no padding)")
    func decodesRfcVectorsNoPadding() {
        let vectors: [(String, String)] = [
            ("Zg", "f"),
            ("Zm8", "fo"),
            ("Zm9v", "foo"),
            ("Zm9vYg", "foob"),
            ("Zm9vYmE", "fooba"),
            ("Zm9vYmFy", "foobar")
        ]
        for (input, expected) in vectors {
            #expect(Data(base64UrlEncoded: input) == Data(expected.utf8))
        }
    }

    @Test("decodes input that already carries padding")
    func decodesWithPadding() {
        #expect(Data(base64UrlEncoded: "Zg==") == Data("f".utf8))
        #expect(Data(base64UrlEncoded: "Zm8=") == Data("fo".utf8))
        #expect(Data(base64UrlEncoded: "Zm9v") == Data("foo".utf8))
    }

    @Test("uses URL-safe alphabet on encode")
    func usesUrlSafeAlphabetOnEncode() {
        let bytes = Data([0xFB, 0xFF, 0xBF])
        #expect(bytes.base64EncodedString() == "+/+/")
        #expect(bytes.base64UrlEncodedString() == "-_-_")
    }

    @Test("accepts URL-safe alphabet on decode")
    func acceptsUrlSafeAlphabetOnDecode() {
        #expect(Data(base64UrlEncoded: "-_-_") == Data([0xFB, 0xFF, 0xBF]))
    }

    @Test("encoded output never contains padding or URL-unsafe characters")
    func encodedOutputIsUrlSafe() {
        for length in 0...64 {
            var bytes = [UInt8]()
            for index in 0..<length {
                bytes.append(UInt8((index * 31 + 7) % 256))
            }
            let encoded = Data(bytes).base64UrlEncodedString()
            #expect(!encoded.contains("+"))
            #expect(!encoded.contains("/"))
            #expect(!encoded.contains("="))
        }
    }

    @Test("round-trips arbitrary lengths")
    func roundTripsArbitraryLengths() {
        for length in 0...130 {
            var bytes = [UInt8]()
            for index in 0..<length {
                bytes.append(UInt8((index * 53 + 11) % 256))
            }
            let original = Data(bytes)
            let encoded = original.base64UrlEncodedString()
            let decoded = Data(base64UrlEncoded: encoded)
            #expect(decoded == original, "round-trip failed at length \(length)")
        }
    }

    @Test("returns nil for input containing characters outside the alphabet")
    func rejectsInvalidCharacters() {
        #expect(Data(base64UrlEncoded: "Zg!!") == nil)
        #expect(Data(base64UrlEncoded: "not base64 at all") == nil)
    }

    @Test("decodes a JWT-style payload segment")
    func decodesJwtPayloadSegment() throws {
        let json = #"{"sub":"test-subject","a":"b"}"#
        let encoded = Data(json.utf8).base64UrlEncodedString()
        let decoded = try #require(Data(base64UrlEncoded: encoded))
        let decodedString = try #require(String(bytes: decoded, encoding: .utf8))
        #expect(decodedString == json)
    }
}

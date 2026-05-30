import Foundation
@testable import StasisClientLib
import Testing

@Suite("ChecksumEncoding")
struct ChecksumEncodingTests {
    @Test("bytes(crc:) encodes zero as a single zero byte")
    func crcZero() {
        #expect(ChecksumEncoding.bytes(crc: 0) == Data([0x00]))
    }

    @Test("bytes(crc:) strips leading zero bytes")
    func crcStripsLeadingZeros() {
        #expect(ChecksumEncoding.bytes(crc: 0x01) == Data([0x01]))
        #expect(ChecksumEncoding.bytes(crc: 0x7F) == Data([0x7F]))
    }

    @Test("bytes(crc:) does not add a sign byte when MSB is clear")
    func crcNoSignPad() {
        #expect(ChecksumEncoding.bytes(crc: 0x7F) == Data([0x7F]))
        #expect(ChecksumEncoding.bytes(crc: 0x227F713C) == Data([0x22, 0x7F, 0x71, 0x3C]))
    }

    @Test("bytes(crc:) prepends a sign byte when MSB is set")
    func crcSignPad() {
        #expect(ChecksumEncoding.bytes(crc: 0x80) == Data([0x00, 0x80]))
        #expect(ChecksumEncoding.bytes(crc: 0xFF) == Data([0x00, 0xFF]))
        #expect(ChecksumEncoding.bytes(crc: 0x80000001) == Data([0x00, 0x80, 0x00, 0x00, 0x01]))
        #expect(ChecksumEncoding.bytes(crc: 0xFFFFFFFF) == Data([0x00, 0xFF, 0xFF, 0xFF, 0xFF]))
    }

    @Test("bytes(crc:) matches the Android fixture CRC32")
    func crcAndroidFixture() {
        #expect(ChecksumEncoding.bytes(crc: 595309308) == Data(base64Encoded: "I3uy/A==")!)
    }

    @Test("bytes(digest:) encodes an empty digest as a single zero byte")
    func digestEmpty() {
        #expect(ChecksumEncoding.bytes(digest: Data()) == Data([0x00]))
    }

    @Test("bytes(digest:) strips leading zero bytes from the magnitude")
    func digestStripsLeadingZeros() {
        #expect(ChecksumEncoding.bytes(digest: Data([0x00, 0x01])) == Data([0x01]))
        #expect(ChecksumEncoding.bytes(digest: Data([0x00, 0x00, 0x01])) == Data([0x01]))
        #expect(ChecksumEncoding.bytes(digest: Data([0x00])) == Data([0x00]))
    }

    @Test("bytes(digest:) does not add a sign byte when MSB is clear")
    func digestNoSignPad() {
        #expect(ChecksumEncoding.bytes(digest: Data([0x7F])) == Data([0x7F]))
        #expect(ChecksumEncoding.bytes(digest: Data([0x7F, 0xFF])) == Data([0x7F, 0xFF]))
    }

    @Test("bytes(digest:) prepends a sign byte when MSB is set")
    func digestSignPad() {
        #expect(ChecksumEncoding.bytes(digest: Data([0x80])) == Data([0x00, 0x80]))
        #expect(ChecksumEncoding.bytes(digest: Data([0xFF])) == Data([0x00, 0xFF]))
        #expect(ChecksumEncoding.bytes(digest: Data([0x80, 0x12])) == Data([0x00, 0x80, 0x12]))
    }

    @Test("bytes(digest:) preserves trailing zero bytes")
    func digestPreservesTrailingZeros() {
        #expect(ChecksumEncoding.bytes(digest: Data([0x01, 0x00, 0x00])) == Data([0x01, 0x00, 0x00]))
        #expect(ChecksumEncoding.bytes(digest: Data([0x80, 0x00])) == Data([0x00, 0x80, 0x00]))
    }

    @Test("bytes(digest:) matches the Android fixture MD5")
    func digestAndroidFixtureMd5() {
        let raw = Data(base64Encoded: "/qgPLbAD1OvEU2AjgUqohQ==")!
        let expected = Data(base64Encoded: "AP6oDy2wA9TrxFNgI4FKqIU=")!
        #expect(ChecksumEncoding.bytes(digest: raw) == expected)
    }

    @Test("bytes(digest:) matches the Android fixture SHA-256")
    func digestAndroidFixtureSha256() {
        let raw = Data(base64Encoded: "1Gi9MyDTQGRrrpUJro3T8m/ddwgHJRoFoxTQp7owsWQ=")!
        let expected = Data(base64Encoded: "ANRovTMg00Bka66VCa6N0/Jv3XcIByUaBaMU0Ke6MLFk")!
        #expect(ChecksumEncoding.bytes(digest: raw) == expected)
    }

    @Test("string(of:) encodes an empty input as \"0\"")
    func stringEmpty() {
        #expect(ChecksumEncoding.string(of: Data()) == "0")
    }

    @Test("string(of:) encodes an all-zero input as \"0\"")
    func stringAllZero() {
        #expect(ChecksumEncoding.string(of: Data([0x00, 0x00, 0x00])) == "0")
    }

    @Test("string(of:) strips leading zero bytes")
    func stringStripsLeadingZeros() {
        #expect(ChecksumEncoding.string(of: Data([0x00, 0x01])) == "1")
        #expect(ChecksumEncoding.string(of: Data([0x00, 0x00, 0xFF])) == "ff")
    }

    @Test("string(of:) drops the leading zero of the most-significant nibble")
    func stringMinWidthFirstByte() {
        #expect(ChecksumEncoding.string(of: Data([0x01])) == "1")
        #expect(ChecksumEncoding.string(of: Data([0x0A])) == "a")
        #expect(ChecksumEncoding.string(of: Data([0x0F])) == "f")
        #expect(ChecksumEncoding.string(of: Data([0x10])) == "10")
    }

    @Test("string(of:) pads subsequent bytes to two hex digits")
    func stringPadsTrailingBytes() {
        #expect(ChecksumEncoding.string(of: Data([0x01, 0x00])) == "100")
        #expect(ChecksumEncoding.string(of: Data([0x01, 0x02, 0x03])) == "10203")
        #expect(ChecksumEncoding.string(of: Data([0xFF, 0x00, 0xFF])) == "ff00ff")
    }

    @Test("string(of:) matches the BigInteger(1, bytes).toString(16) for the 0x2A checksum case")
    func stringMagnitude42() {
        #expect(ChecksumEncoding.string(of: Data([0x2A])) == "2a")
    }
}

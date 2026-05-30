import Foundation

public enum ChecksumEncoding {
    public static func bytes(crc value: UInt64) -> Data {
        if value == 0 { return Data([0x00]) }
        let raw = withUnsafeBytes(of: value.bigEndian) { Data($0) }
        return normalize(raw)
    }

    public static func bytes(digest raw: Data) -> Data {
        normalize(raw)
    }

    public static func string(of data: Data) -> String {
        var bytes = Array(data)
        while bytes.first == 0 { bytes.removeFirst() }
        if bytes.isEmpty { return "0" }
        let first = String(bytes[0], radix: 16)
        let rest = bytes.dropFirst().map { String(format: "%02x", $0) }.joined()
        return first + rest
    }

    private static func normalize(_ raw: Data) -> Data {
        var start = raw.startIndex
        while start < raw.endIndex && raw[start] == 0x00 && raw.distance(from: start, to: raw.endIndex) > 1 {
            start = raw.index(after: start)
        }
        let stripped = raw[start..<raw.endIndex]
        if stripped.isEmpty { return Data([0x00]) }
        if stripped.first! & 0x80 != 0 {
            return Data([0x00]) + stripped
        }
        return Data(stripped)
    }
}

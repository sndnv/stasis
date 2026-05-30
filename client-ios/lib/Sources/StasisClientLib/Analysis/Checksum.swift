import CryptoKit
import Foundation
import zlib

public protocol Checksum: Sendable {
    func calculate(file: URL) async throws -> Data
}

public enum Checksums: Sendable, Equatable, Hashable {
    case crc32
    case md5
    case sha1
    case sha256

    public static func apply(_ checksum: String) throws -> Checksums {
        switch checksum.lowercased() {
        case "crc32": return .crc32
        case "md5": return .md5
        case "sha1": return .sha1
        case "sha256": return .sha256
        default: throw ChecksumError.unsupported(checksum)
        }
    }

    public static func crc32(file: URL) async throws -> Data {
        try await Task.detached(priority: .userInitiated) {
            var crc: uLong = zlib.crc32(0, nil, 0)
            try Self.streamFile(file) { buffer in
                crc = buffer.withUnsafeBytes { raw in
                    zlib.crc32(crc, raw.bindMemory(to: Bytef.self).baseAddress, uInt(raw.count))
                }
            }
            return ChecksumEncoding.bytes(crc: UInt64(crc))
        }.value
    }

    public static func md5(file: URL) async throws -> Data {
        try await digest(file: file, algorithm: "MD5")
    }

    public static func sha1(file: URL) async throws -> Data {
        try await digest(file: file, algorithm: "SHA-1")
    }

    public static func sha256(file: URL) async throws -> Data {
        try await digest(file: file, algorithm: "SHA-256")
    }

    public static func digest(file: URL, algorithm: String) async throws -> Data {
        try await Task.detached(priority: .userInitiated) {
            let raw = try Self.computeHash(file: file, algorithm: algorithm)
            return ChecksumEncoding.bytes(digest: raw)
        }.value
    }

    private static func computeHash(file: URL, algorithm: String) throws -> Data {
        switch algorithm.lowercased() {
        case "md5":
            var hasher = Insecure.MD5()
            try Self.streamFile(file) { hasher.update(data: $0) }
            return Data(hasher.finalize())
        case "sha-1", "sha1":
            var hasher = Insecure.SHA1()
            try Self.streamFile(file) { hasher.update(data: $0) }
            return Data(hasher.finalize())
        case "sha-256", "sha256":
            var hasher = SHA256()
            try Self.streamFile(file) { hasher.update(data: $0) }
            return Data(hasher.finalize())
        case "sha-384", "sha384":
            var hasher = SHA384()
            try Self.streamFile(file) { hasher.update(data: $0) }
            return Data(hasher.finalize())
        case "sha-512", "sha512":
            var hasher = SHA512()
            try Self.streamFile(file) { hasher.update(data: $0) }
            return Data(hasher.finalize())
        default:
            throw ChecksumError.unsupported(algorithm)
        }
    }

    private static func streamFile(_ file: URL, _ consume: (Data) -> Void) throws {
        guard let handle = try? FileHandle(forReadingFrom: file) else {
            throw ChecksumError.fileUnreadable(file)
        }
        defer { try? handle.close() }
        let chunkSize = 64 * 1024
        while autoreleasepool(invoking: {
            let chunk = handle.readData(ofLength: chunkSize)
            if chunk.isEmpty { return false }
            consume(chunk)
            return true
        }) {}
    }

}

extension Checksums: Checksum {
    public func calculate(file: URL) async throws -> Data {
        switch self {
        case .crc32: return try await Checksums.crc32(file: file)
        case .md5: return try await Checksums.md5(file: file)
        case .sha1: return try await Checksums.sha1(file: file)
        case .sha256: return try await Checksums.sha256(file: file)
        }
    }
}

public enum ChecksumError: Error, Equatable, Sendable {
    case unsupported(String)
    case fileUnreadable(URL)
}

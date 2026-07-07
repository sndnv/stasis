import Foundation
import zlib

public struct Deflate: Compressor {
    public static let shared = Deflate()

    public let name = "deflate"

    private init() {}

    public func compress(_ data: Data) throws -> Data {
        var stream = z_stream()
        let initStatus = deflateInit2_(
            &stream,
            Z_BEST_COMPRESSION,
            Z_DEFLATED,
            15,
            8,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            Int32(MemoryLayout<z_stream>.size)
        )
        guard initStatus == Z_OK else { throw DeflateError.initialization(code: initStatus) }
        defer { _ = deflateEnd(&stream) }
        return try Deflate.processStream(stream: &stream, input: data, flush: Z_FINISH, step: deflate)
    }

    public func decompress(_ data: Data) throws -> Data {
        var stream = z_stream()
        let initStatus = inflateInit2_(&stream, 15, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard initStatus == Z_OK else { throw DeflateError.initialization(code: initStatus) }
        defer { _ = inflateEnd(&stream) }
        return try Deflate.processStream(stream: &stream, input: data, flush: Z_NO_FLUSH, step: inflate)
    }

    public func encode(_ source: AsyncThrowingStream<Data, Error>) -> AsyncThrowingStream<Data, Error> {
        ZlibStream.transform(source, mode: .compress, windowBits: 15)
    }

    public func decode(_ source: AsyncThrowingStream<Data, Error>) -> AsyncThrowingStream<Data, Error> {
        ZlibStream.transform(source, mode: .decompress, windowBits: 15)
    }

    private static let chunkSize = 64 * 1024

    static func validateInputSize(_ count: Int) throws {
        guard count <= Int(UInt32.max) else {
            throw DeflateError.processing(
                code: Z_STREAM_ERROR,
                message: "input exceeds maximum zlib chunk size (\(UInt32.max) bytes)"
            )
        }
    }

    private static func processStream(
        stream: inout z_stream,
        input: Data,
        flush: Int32,
        step: (UnsafeMutablePointer<z_stream>?, Int32) -> Int32
    ) throws -> Data {
        var output = Data()
        var chunk = Data(count: chunkSize)

        return try input.withUnsafeBytes { (inputBytes: UnsafeRawBufferPointer) -> Data in
            try validateInputSize(inputBytes.count)
            stream.next_in = UnsafeMutablePointer<Bytef>(
                mutating: inputBytes.bindMemory(to: Bytef.self).baseAddress
            )
            stream.avail_in = uInt(inputBytes.count)

            while true {
                let status = chunk.withUnsafeMutableBytes { (chunkBytes: UnsafeMutableRawBufferPointer) -> Int32 in
                    guard let chunkBase = chunkBytes.bindMemory(to: Bytef.self).baseAddress else {
                        return Z_STREAM_ERROR
                    }
                    stream.next_out = chunkBase
                    stream.avail_out = uInt(chunkBytes.count)
                    return step(&stream, flush)
                }

                if status != Z_OK && status != Z_STREAM_END && status != Z_BUF_ERROR {
                    throw DeflateError.processing(
                        code: status,
                        message: stream.msg.flatMap { String(cString: $0) }
                    )
                }

                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 {
                    output.append(chunk.prefix(produced))
                }

                if status == Z_STREAM_END { break }
                if stream.avail_in == 0 && stream.avail_out > 0 { break }
            }

            return output
        }
    }
}

public enum DeflateError: Error, Equatable, Sendable {
    case initialization(code: Int32)
    case processing(code: Int32, message: String?)
}

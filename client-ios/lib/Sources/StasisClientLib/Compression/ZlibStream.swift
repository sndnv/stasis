import Foundation
import zlib

public enum ZlibStream {
    public enum Mode: Sendable {
        case compress
        case decompress
    }

    private static let chunkSize = 64 * 1024

    public static func transform(
        _ source: AsyncThrowingStream<Data, Error>,
        mode: Mode,
        windowBits: Int32
    ) -> AsyncThrowingStream<Data, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    var stream = z_stream()
                    try initialize(&stream, mode: mode, windowBits: windowBits)
                    defer { finalize(&stream, mode: mode) }

                    let step = self.step(for: mode)

                    for try await input in source {
                        try Task.checkCancellation()
                        try pump(&stream, step: step, input: input, flush: Z_NO_FLUSH) { output in
                            continuation.yield(output)
                        }
                    }

                    try pump(&stream, step: step, input: Data(), flush: Z_FINISH) { output in
                        continuation.yield(output)
                    }

                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private static func step(for mode: Mode) -> (UnsafeMutablePointer<z_stream>?, Int32) -> Int32 {
        switch mode {
        case .compress: return deflate
        case .decompress: return inflate
        }
    }

    private static func initialize(_ stream: inout z_stream, mode: Mode, windowBits: Int32) throws {
        let status: Int32
        switch mode {
        case .compress:
            status = deflateInit2_(
                &stream,
                Z_BEST_COMPRESSION,
                Z_DEFLATED,
                windowBits,
                8,
                Z_DEFAULT_STRATEGY,
                ZLIB_VERSION,
                Int32(MemoryLayout<z_stream>.size)
            )
        case .decompress:
            status = inflateInit2_(&stream, windowBits, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        }
        guard status == Z_OK else { throw DeflateError.initialization(code: status) }
    }

    private static func finalize(_ stream: inout z_stream, mode: Mode) {
        switch mode {
        case .compress: _ = deflateEnd(&stream)
        case .decompress: _ = inflateEnd(&stream)
        }
    }

    private static func pump(
        _ stream: inout z_stream,
        step: (UnsafeMutablePointer<z_stream>?, Int32) -> Int32,
        input: Data,
        flush: Int32,
        emit: (Data) -> Void
    ) throws {
        var output = Data(count: chunkSize)

        try input.withUnsafeBytes { (inputBytes: UnsafeRawBufferPointer) in
            try Deflate.validateInputSize(inputBytes.count)
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: inputBytes.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(inputBytes.count)

            repeat {
                let status = output.withUnsafeMutableBytes { (outputBytes: UnsafeMutableRawBufferPointer) -> Int32 in
                    guard let base = outputBytes.bindMemory(to: Bytef.self).baseAddress else { return Z_STREAM_ERROR }
                    stream.next_out = base
                    stream.avail_out = uInt(outputBytes.count)
                    return step(&stream, flush)
                }

                guard status == Z_OK || status == Z_STREAM_END || status == Z_BUF_ERROR else {
                    throw DeflateError.processing(code: status, message: stream.msg.flatMap { String(cString: $0) })
                }

                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 { emit(Data(output.prefix(produced))) }

                if status == Z_STREAM_END { break }
            } while stream.avail_out == 0
        }
    }
}

import Foundation
import Gzip

public struct Gzip: Compressor {
    public static let shared = Gzip()

    public let name = "gzip"

    private init() {}

    public func compress(_ data: Data) throws -> Data {
        try data.gzipped()
    }

    public func decompress(_ data: Data) throws -> Data {
        try data.gunzipped()
    }

    public func encode(_ source: AsyncThrowingStream<Data, Error>) -> AsyncThrowingStream<Data, Error> {
        ZlibStream.transform(source, mode: .compress, windowBits: 31)
    }

    public func decode(_ source: AsyncThrowingStream<Data, Error>) -> AsyncThrowingStream<Data, Error> {
        ZlibStream.transform(source, mode: .decompress, windowBits: 31)
    }
}

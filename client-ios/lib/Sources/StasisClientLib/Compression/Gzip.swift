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
}

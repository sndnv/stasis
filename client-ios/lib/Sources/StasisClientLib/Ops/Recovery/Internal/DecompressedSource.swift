import Foundation

public enum DecompressedSource {
    public static func decompress(
        _ source: AsyncThrowingStream<Data, Error>,
        decompressor: any CompressionDecoder
    ) -> AsyncThrowingStream<Data, Error> {
        decompressor.decode(source)
    }
}

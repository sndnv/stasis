import Foundation

public enum DecompressedSource {
    public static func decompress(
        _ source: AsyncThrowingStream<Data, Error>,
        decompressor: any CompressionDecoder
    ) -> AsyncThrowingStream<Data, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    var collected = Data()
                    for try await chunk in source {
                        collected.append(chunk)
                    }
                    let decompressed = try decompressor.decompress(collected)
                    continuation.yield(decompressed)
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}

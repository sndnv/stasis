import Foundation

public enum FileByteSource {
    public static func read(_ url: URL, chunkSize: Int) -> AsyncThrowingStream<Data, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let handle = try FileHandle(forReadingFrom: url)
                    defer { try? handle.close() }
                    while true {
                        try Task.checkCancellation()
                        let chunk = try autoreleasepool { try handle.read(upToCount: chunkSize) }
                        guard let chunk, !chunk.isEmpty else { break }
                        continuation.yield(chunk)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}

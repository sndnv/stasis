import Foundation

func makeDataStream(_ chunks: [Data]) -> AsyncThrowingStream<Data, Error> {
    AsyncThrowingStream { continuation in
        for chunk in chunks {
            continuation.yield(chunk)
        }
        continuation.finish()
    }
}

func makeDataStream(_ data: Data) -> AsyncThrowingStream<Data, Error> {
    makeDataStream([data])
}

func makeDataStream(_ chunks: [Data], thenThrow error: any Error) -> AsyncThrowingStream<Data, Error> {
    AsyncThrowingStream { continuation in
        for chunk in chunks {
            continuation.yield(chunk)
        }
        continuation.finish(throwing: error)
    }
}

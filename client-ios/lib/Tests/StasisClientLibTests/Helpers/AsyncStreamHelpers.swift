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

func collectData(_ stream: AsyncThrowingStream<Data, Error>) async throws -> Data {
    var data = Data()
    for try await chunk in stream { data.append(chunk) }
    return data
}

import Foundation

public protocol CompressionEncoder: Sendable {
    var name: String { get }
    func compress(_ data: Data) throws -> Data
    func encode(_ source: AsyncThrowingStream<Data, Error>) -> AsyncThrowingStream<Data, Error>
}

public protocol CompressionDecoder: Sendable {
    var name: String { get }
    func decompress(_ data: Data) throws -> Data
    func decode(_ source: AsyncThrowingStream<Data, Error>) -> AsyncThrowingStream<Data, Error>
}

public protocol Compressor: CompressionEncoder, CompressionDecoder {}

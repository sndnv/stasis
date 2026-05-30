import Foundation

public protocol CompressionEncoder: Sendable {
    var name: String { get }
    func compress(_ data: Data) throws -> Data
}

public protocol CompressionDecoder: Sendable {
    var name: String { get }
    func decompress(_ data: Data) throws -> Data
}

public protocol Compressor: CompressionEncoder, CompressionDecoder {}

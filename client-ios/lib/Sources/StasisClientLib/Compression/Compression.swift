import Foundation

public protocol Compression: Sendable {
    var defaultCompression: any Compressor { get }
    var disabledExtensions: Set<String> { get }
    func algorithmFor(entity: URL) -> String
    func encoderFor(entity: SourceEntity) throws -> any CompressionEncoder
    func decoderFor(entity: TargetEntity) throws -> any CompressionDecoder
}

public extension Compression {
    func algorithmFor(entity: URL) -> String {
        compressionAllowed(for: entity) ? defaultCompression.name : Identity.shared.name
    }

    func encoderFor(entity: SourceEntity) throws -> any CompressionEncoder {
        try compressor(for: entity.currentMetadata)
    }

    func decoderFor(entity: TargetEntity) throws -> any CompressionDecoder {
        try compressor(for: entity.existingMetadata)
    }

    private func compressionAllowed(for entity: URL) -> Bool {
        let path = entity.path
        return !disabledExtensions.contains { extensionSuffix in
            path.hasSuffix(".\(extensionSuffix)")
        }
    }

    private func compressor(for metadata: EntityMetadata) throws -> any Compressor {
        switch metadata {
        case .file(let file):
            return try Compressions.fromString(file.compression)
        case .directory(let directory):
            throw CompressionError.expectedFileGotDirectory(path: directory.path)
        }
    }
}

public enum Compressions: Sendable {
    public static func create(defaultCompression: String, disabledExtensions: String) throws -> any Compression {
        try create(
            defaultCompression: fromString(defaultCompression),
            disabledExtensions: Set(
                disabledExtensions
                    .split(separator: ",")
                    .map { $0.trimmingCharacters(in: .whitespaces) }
                    .filter { !$0.isEmpty }
            )
        )
    }

    public static func create(defaultCompression: any Compressor, disabledExtensions: Set<String>) -> any Compression {
        ConfigurableCompression(defaultCompression: defaultCompression, disabledExtensions: disabledExtensions)
    }

    public static func fromString(_ compression: String) throws -> any Compressor {
        switch compression.lowercased() {
        case Deflate.shared.name: return Deflate.shared
        case Gzip.shared.name: return Gzip.shared
        case Identity.shared.name: return Identity.shared
        default: throw CompressionError.unsupported(compression)
        }
    }
}

public enum CompressionError: Error, Equatable, Sendable {
    case unsupported(String)
    case expectedFileGotDirectory(path: String)
}

private struct ConfigurableCompression: Compression {
    let defaultCompression: any Compressor
    let disabledExtensions: Set<String>
}

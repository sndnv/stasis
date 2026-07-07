import Foundation

public struct TargetEntity: Sendable, Equatable, Hashable {
    public let ref: EntityRef
    public let destination: Destination
    public let existingMetadata: EntityMetadata
    public let currentMetadata: EntityMetadata?

    public init(
        ref: EntityRef,
        destination: Destination,
        existingMetadata: EntityMetadata,
        currentMetadata: EntityMetadata?
    ) throws {
        if let current = currentMetadata, !existingMetadata.sameKind(as: current) {
            throw EntityMetadataMismatch(
                currentPath: current.path,
                existingPath: existingMetadata.path
            )
        }
        self.ref = ref
        self.destination = destination
        self.existingMetadata = existingMetadata
        self.currentMetadata = currentMetadata
    }

    public var originalRef: EntityRef {
        ref.mapFilesystem { _ in URL(fileURLWithPath: existingMetadata.path) }
    }

    public var originalPath: URL {
        URL(fileURLWithPath: existingMetadata.path)
    }

    public var destinationRef: EntityRef {
        switch destination {
        case .default:
            return originalRef
        case .directory(let path, let keepDefaultStructure):
            return originalRef.flatMap { reference in
                let original: URL = switch reference {
                case .filesystem(let url): url
                case .library(_, let libraryPath): URL(fileURLWithPath: libraryPath)
                }
                let target = keepDefaultStructure
                    ? path.appendingPathComponent(original.path)
                    : path.appendingPathComponent(original.lastPathComponent)
                return .filesystem(target)
            }
        }
    }

    public var destinationPath: URL {
        switch destination {
        case .default:
            originalPath
        case .directory(let path, let keepDefaultStructure):
            if keepDefaultStructure {
                path.appendingPathComponent(originalPath.path)
            } else {
                path.appendingPathComponent(originalPath.lastPathComponent)
            }
        }
    }

    public var hasChanged: Bool {
        guard let current = currentMetadata else { return true }
        return existingMetadata.hasChanged(comparedTo: current)
    }

    public var hasContentChanged: Bool {
        if let existingContent = existingMetadata.content, let currentContent = currentMetadata?.content {
            return existingContent.size != currentContent.size || existingContent.checksum != currentContent.checksum
        }
        if existingMetadata.content != nil, currentMetadata == nil {
            return true
        }
        return false
    }

    public enum Destination: Sendable, Equatable, Hashable {
        case `default`
        case directory(path: URL, keepDefaultStructure: Bool)
    }
}

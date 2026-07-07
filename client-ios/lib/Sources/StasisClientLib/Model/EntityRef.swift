import Foundation
import fsi

public enum EntityRef: Sendable, Equatable, Hashable {
    case filesystem(URL)
    case library(scheme: String, path: String)

    public var key: String {
        switch self {
        case .filesystem(let url): url.path
        case .library(let scheme, let path): "\(scheme)\(Schemes.Delimiter)\(path)"
        }
    }

    public func mapFilesystem(_ transform: (URL) -> URL) -> EntityRef {
        switch self {
        case .filesystem(let url): .filesystem(transform(url))
        case .library: self
        }
    }

    public func mapLibrary(_ transform: (String, String) -> (String, String)) -> EntityRef {
        switch self {
        case .filesystem: return self
        case .library(let scheme, let path):
            let (mappedScheme, mappedPath) = transform(scheme, path)
            return .library(scheme: mappedScheme, path: mappedPath)
        }
    }

    public func flatMap(_ transform: (EntityRef) -> EntityRef) -> EntityRef {
        transform(self)
    }

    public func asFilesystem() throws -> URL {
        switch self {
        case .filesystem(let url): return url
        case .library: throw InvalidArgumentError("Requested a filesystem reference but [\(key)] found")
        }
    }

    public func asLibrary() throws -> (scheme: String, path: String) {
        switch self {
        case .filesystem: throw InvalidArgumentError("Requested a library reference but [\(key)] found")
        case .library(let scheme, let path): return (scheme, path)
        }
    }

    public static func `default`(key: String) -> EntityRef {
        let (scheme, rest) = Schemes.extract(key)
        switch scheme {
        case .none: return .filesystem(URL(fileURLWithPath: key))
        case .some(let scheme): return .library(scheme: scheme, path: rest)
        }
    }
}

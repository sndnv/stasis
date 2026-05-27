import Foundation
import fsi

public enum FilesystemMetadata: Sendable, Equatable, Hashable {
    case asTrie(underlying: TrieIndex<EntityState>)

    public var underlying: any Index<EntityState> {
        switch self {
        case .asTrie(let underlying): underlying
        }
    }

    public enum EntityState: Sendable, Equatable, Hashable {
        case new
        case existing(entry: DatasetEntryId)
        case updated
    }
}

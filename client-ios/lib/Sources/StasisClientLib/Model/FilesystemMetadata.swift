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

    public static func empty() -> FilesystemMetadata {
        FilesystemMetadata(entities: [:])
    }

    public init(changes: some Sequence<String>) {
        var index = TrieIndex<EntityState>()
        index.putAll(Array(changes)) { _, _ in .new }
        self = .asTrie(underlying: index)
    }

    public func get(_ entity: String) -> EntityState? {
        underlying.get(entity)
    }

    public func collect<T>(_ transform: (String, EntityState) -> T?) -> [T] {
        underlying.collect(transform)
    }

    public func search(_ regex: NSRegularExpression) -> [String: EntityState] {
        underlying.search(regex)
    }

    public func updated(changes: some Sequence<String>, latestEntry: DatasetEntryId) -> FilesystemMetadata {
        guard case .asTrie(var trie) = self else { return self }
        trie.replaceAll { _, state in
            switch state {
            case .new, .updated: .existing(entry: latestEntry)
            case .existing: state
            }
        }
        trie.putAll(Array(changes)) { _, existing in
            existing != nil ? .updated : .new
        }
        return .asTrie(underlying: trie)
    }
}

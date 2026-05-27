import Foundation

public struct Manifest: Sendable, Equatable, Hashable, Codable {
    public let crate: CrateId
    public let size: Int64
    public let copies: Int
    public let origin: NodeId
    public let source: NodeId
    public let destinations: [NodeId]
    public let created: Date

    public init(
        crate: CrateId,
        size: Int64,
        copies: Int,
        origin: NodeId,
        source: NodeId,
        destinations: [NodeId],
        created: Date
    ) {
        self.crate = crate
        self.size = size
        self.copies = copies
        self.origin = origin
        self.source = source
        self.destinations = destinations
        self.created = created
    }

    public init(
        crate: CrateId,
        size: Int64,
        copies: Int,
        origin: NodeId,
        source: NodeId
    ) {
        self.init(
            crate: crate,
            size: size,
            copies: copies,
            origin: origin,
            source: source,
            destinations: [],
            created: Date()
        )
    }
}

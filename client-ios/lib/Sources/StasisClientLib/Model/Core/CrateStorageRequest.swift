import Foundation

public typealias CrateStorageRequestId = UUID

public struct CrateStorageRequest: Sendable, Equatable, Hashable, Codable {
    public let id: CrateStorageRequestId
    public let crate: CrateId
    public let size: Int64
    public let copies: Int
    public let origin: NodeId
    public let source: NodeId

    public init(
        id: CrateStorageRequestId,
        crate: CrateId,
        size: Int64,
        copies: Int,
        origin: NodeId,
        source: NodeId
    ) {
        self.id = id
        self.crate = crate
        self.size = size
        self.copies = copies
        self.origin = origin
        self.source = source
    }

    public init(manifest: Manifest) {
        self.init(
            id: UUID(),
            crate: manifest.crate,
            size: manifest.size,
            copies: manifest.copies,
            origin: manifest.origin,
            source: manifest.source
        )
    }
}

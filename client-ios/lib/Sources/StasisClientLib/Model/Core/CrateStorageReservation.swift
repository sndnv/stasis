import Foundation

public typealias CrateStorageReservationId = UUID

public struct CrateStorageReservation: Sendable, Equatable, Hashable, Codable {
    public let id: CrateStorageReservationId
    public let crate: CrateId
    public let size: Int64
    public let copies: Int
    public let origin: NodeId
    public let target: NodeId

    public init(
        id: CrateStorageReservationId,
        crate: CrateId,
        size: Int64,
        copies: Int,
        origin: NodeId,
        target: NodeId
    ) {
        self.id = id
        self.crate = crate
        self.size = size
        self.copies = copies
        self.origin = origin
        self.target = target
    }
}

import Foundation

public protocol ServerTracker: Sendable {
    func reachable(server: String) async
    func unreachable(server: String) async
}

public struct ServerState: Sendable, Equatable, Hashable {
    public let reachable: Bool
    public let timestamp: Date

    public init(reachable: Bool, timestamp: Date) {
        self.reachable = reachable
        self.timestamp = timestamp
    }
}

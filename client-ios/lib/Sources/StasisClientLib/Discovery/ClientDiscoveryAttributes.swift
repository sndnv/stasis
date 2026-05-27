import Foundation

public struct ClientDiscoveryAttributes: Sendable, Equatable, Hashable, ServiceDiscoveryClientAttributes {
    public let user: UserId
    public let device: DeviceId
    public let node: NodeId

    public init(user: UserId, device: DeviceId, node: NodeId) {
        self.user = user
        self.device = device
        self.node = node
    }

    public func asServiceDiscoveryRequest(isInitialRequest: Bool) -> ServiceDiscoveryRequest {
        ServiceDiscoveryRequest(
            isInitialRequest: isInitialRequest,
            attributes: [
                "user": user.uuidString.lowercased(),
                "device": device.uuidString.lowercased(),
                "node": node.uuidString.lowercased()
            ]
        )
    }
}

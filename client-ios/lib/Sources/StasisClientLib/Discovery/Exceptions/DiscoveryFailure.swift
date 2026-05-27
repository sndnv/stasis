public struct DiscoveryFailure: Error, Equatable {
    public let message: String

    public init(message: String) {
        self.message = message
    }
}

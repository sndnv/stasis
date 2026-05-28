import Foundation

public protocol HttpCredentialsProvider: Sendable {
    func credentials() async -> HttpCredentials
}

public struct StaticHttpCredentialsProvider: HttpCredentialsProvider {
    public let value: HttpCredentials

    public init(_ value: HttpCredentials) {
        self.value = value
    }

    public func credentials() async -> HttpCredentials {
        value
    }
}

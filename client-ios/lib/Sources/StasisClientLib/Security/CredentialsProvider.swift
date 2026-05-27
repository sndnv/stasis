import Foundation

public protocol CredentialsProvider: Sendable {
    func credentials() async -> HttpCredentials
}

public struct StaticCredentialsProvider: CredentialsProvider {
    public let value: HttpCredentials

    public init(_ value: HttpCredentials) {
        self.value = value
    }

    public func credentials() async -> HttpCredentials {
        value
    }
}

import Foundation

public struct MissingDeviceSecret: Error, Equatable, LocalizedError {
    public init() {}

    public var errorDescription: String? { "No device secret is available" }
}

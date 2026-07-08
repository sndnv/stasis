import Foundation

public typealias OperationId = UUID

public protocol Operation: Sendable {
    var id: OperationId { get }
    func start() async throws
    func stop()
    var type: OperationType { get }
}

public enum Operations {
    public static func generateId() -> OperationId {
        UUID()
    }
}

public enum OperationType: String, Sendable, CaseIterable {
    case backup = "Backup"
    case recovery = "Recovery"
    case expiration = "Expiration"
    case validation = "Validation"
    case keyRotation = "KeyRotation"
    case garbageCollection = "GarbageCollection"

    public init(stringValue: String) throws {
        guard let value = OperationType(rawValue: stringValue) else {
            throw OperationTypeError.unexpected(stringValue)
        }
        self = value
    }
}

public enum OperationTypeError: Error, Equatable, LocalizedError {
    case unexpected(String)

    public var errorDescription: String? {
        switch self {
        case .unexpected(let value):
            "Unexpected operation type provided: [\(value)]"
        }
    }
}

public struct OperationProgress: Sendable, Equatable, Hashable {
    public let started: Date
    public let total: Int
    public let processed: Int
    public let failures: Int
    public let completed: Date?

    public init(started: Date, total: Int, processed: Int, failures: Int, completed: Date?) {
        self.started = started
        self.total = total
        self.processed = processed
        self.failures = failures
        self.completed = completed
    }
}

public enum OperationRestriction: Sendable, Equatable, Hashable {
    case noConnection
    case limitedNetwork

    public var summary: String {
        switch self {
        case .noConnection: "no network connection"
        case .limitedNetwork: "restricted or metered network"
        }
    }
}

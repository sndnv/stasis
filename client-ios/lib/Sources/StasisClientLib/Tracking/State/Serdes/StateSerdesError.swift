import Foundation

public enum StateSerdesError: Error, Equatable, LocalizedError {
    case invalidOperationId(String)

    public var errorDescription: String? {
        switch self {
        case .invalidOperationId(let value):
            "Invalid operation ID [\(value)]"
        }
    }
}

import Foundation

public struct Rule: Sendable, Equatable, Hashable {
    public let id: Int64
    public let operation: RuleOperation
    public let source: String
    public let pattern: String
    public let definition: DatasetDefinitionId?

    public init(
        id: Int64,
        operation: RuleOperation,
        source: String,
        pattern: String,
        definition: DatasetDefinitionId?
    ) {
        self.id = id
        self.operation = operation
        self.source = source
        self.pattern = pattern
        self.definition = definition
    }

    public func asString() -> String {
        let operationAsString: String = switch operation {
        case .include: "+"
        case .exclude: "-"
        }
        let definitionAsString: String = switch definition {
        case .none: ""
        case .some(let id): "(\(id))"
        }
        return "\(operationAsString) \(source) \(pattern) \(definitionAsString)"
            .trimmingCharacters(in: .whitespaces)
    }
}

public enum RuleOperation: Sendable, Equatable, Hashable {
    case include
    case exclude
}

import Foundation
import StasisClientLib
import SwiftData

public enum RuleEntityError: Error, Equatable {
    case unknownOperation(String)
}

@Model
public final class RuleEntity {
    @Attribute(.unique) public var id: Int64
    public var operationRaw: String
    public var source: String
    public var pattern: String
    public var definition: UUID?

    public init(
        id: Int64 = 0,
        operation: RuleOperation,
        source: String,
        pattern: String,
        definition: DatasetDefinitionId?
    ) {
        self.id = id
        self.operationRaw = RuleEntity.encode(operation)
        self.source = source
        self.pattern = pattern
        self.definition = definition
    }

    public static func encode(_ operation: RuleOperation) -> String {
        switch operation {
        case .include: "include"
        case .exclude: "exclude"
        }
    }

    public static func decode(_ raw: String) throws -> RuleOperation {
        switch raw {
        case "include": .include
        case "exclude": .exclude
        default: throw RuleEntityError.unknownOperation(raw)
        }
    }
}

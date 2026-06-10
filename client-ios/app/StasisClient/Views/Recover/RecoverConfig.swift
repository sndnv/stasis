import Foundation
import StasisClientLib

struct RecoverConfig: Equatable {
    var definition: DatasetDefinitionId?
    var recoverySource: RecoverySource
    var pathQuery: String
    var destination: String
    var discardPaths: Bool

    static let initial = RecoverConfig(
        definition: nil,
        recoverySource: .latest,
        pathQuery: "",
        destination: "",
        discardPaths: false
    )

    func validate() -> ValidationResult {
        guard definition != nil else { return .missingDefinition }
        switch recoverySource {
        case .latest, .until: return .valid
        case .entry(let id): return id == nil ? .missingEntry : .valid
        }
    }

    var recoveryPathQuery: Recovery.PathQuery? {
        let trimmed = pathQuery.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        return try? Recovery.PathQuery.parse(trimmed)
    }

    var recoveryDestination: Recovery.Destination? {
        let trimmed = destination.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        return Recovery.Destination(path: trimmed, keepStructure: !discardPaths)
    }

    enum RecoverySource: Equatable {
        case latest
        case entry(DatasetEntryId?)
        case until(Date)

        var kind: Kind {
            switch self {
            case .latest: .latest
            case .entry: .entry
            case .until: .until
            }
        }

        enum Kind: CaseIterable, Identifiable, Hashable {
            case latest, entry, until
            var id: Self { self }
            var label: String {
                switch self {
                case .latest: "Latest"
                case .entry: "Entry"
                case .until: "Until"
                }
            }
        }
    }

    enum ValidationResult: Equatable {
        case valid
        case missingDefinition
        case missingEntry

        var buttonLabel: String {
            switch self {
            case .valid: "Run Recover"
            case .missingDefinition: "Pick a Definition"
            case .missingEntry: "Pick an Entry"
            }
        }
    }
}

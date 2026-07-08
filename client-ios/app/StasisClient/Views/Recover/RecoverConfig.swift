import Foundation
import StasisClientLib

struct RecoverConfig: Equatable {
    var definition: DatasetDefinitionId?
    var recoverySource: RecoverySource
    var sources: Set<RecoverySourceKind>

    static let allSources: [RecoverySourceKind] =
        [.filesystem] + LibrarySource.all.map { .library(scheme: $0.scheme) }

    static let initial = RecoverConfig(
        definition: nil,
        recoverySource: .latest,
        sources: Set(allSources)
    )

    func validate() -> ValidationResult {
        guard definition != nil else { return .missingDefinition }
        guard !sources.isEmpty else { return .missingSources }
        switch recoverySource {
        case .latest, .until: return .valid
        case .entry(let id): return id == nil ? .missingEntry : .valid
        }
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
        case missingSources

        var buttonLabel: String {
            switch self {
            case .valid: "Run Recover"
            case .missingDefinition: "Pick a Definition"
            case .missingEntry: "Pick an Entry"
            case .missingSources: "Select Sources"
            }
        }
    }
}

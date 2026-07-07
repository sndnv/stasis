import Foundation
import StasisClientLib

enum SourceRules {
    static func matching(_ rules: [Rule], scheme: String) -> [Rule] {
        rules.filter { SourceUri.scheme($0.source) == scheme }
    }

    static func isEnabled(_ rules: [Rule], scheme: String) -> Bool {
        !enabledSets(rules, scheme: scheme).isEmpty
    }

    static func isInDefault(_ rules: [Rule], scheme: String) -> Bool {
        enabledSets(rules, scheme: scheme).contains(nil)
    }

    static func allSets(_ rules: [Rule]) -> Set<DatasetDefinitionId?> {
        var sets: Set<DatasetDefinitionId?> = [nil]
        for rule in rules { sets.insert(rule.definition) }
        return sets
    }

    static func enabledSets(_ rules: [Rule], scheme: String) -> Set<DatasetDefinitionId?> {
        Set(
            rules
                .filter { $0.operation == .include && SourceUri.scheme($0.source) == scheme }
                .map { $0.definition }
        )
    }

    static func setsMissing(_ rules: [Rule], scheme: String) -> Set<DatasetDefinitionId?> {
        allSets(rules).subtracting(enabledSets(rules, scheme: scheme))
    }

    static func defaultLibrarySchemes(_ rules: [Rule]) -> Set<String> {
        Set(
            rules
                .filter { $0.definition == nil && $0.operation == .include }
                .compactMap { SourceUri.scheme($0.source) }
        )
    }
}

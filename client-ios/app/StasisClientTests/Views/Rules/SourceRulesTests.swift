import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("SourceRules")
struct SourceRulesTests {
    private let definitionA = UUID(uuidString: "E32E12A1-BD05-4CE4-9E39-71A717861A4F")!
    private let definitionB = UUID(uuidString: "4F6B1902-0092-4827-88D4-E40DDA8A62C0")!

    private func rule(
        _ id: Int64,
        _ operation: RuleOperation,
        _ source: String,
        definition: DatasetDefinitionId?
    ) -> Rule {
        Rule(id: id, operation: operation, source: source, pattern: "*", definition: definition)
    }

    private var mixedRules: [Rule] {
        [
            rule(0, .include, "calendar:/", definition: nil),
            rule(1, .include, "/test", definition: nil),
            rule(2, .include, "calendar:/", definition: definitionA),
            rule(3, .include, "/test", definition: definitionA),
            rule(4, .include, "contacts:/", definition: definitionB)
        ]
    }

    @Test("reports a source enabled when an include rule exists for the scheme")
    func enabledWhenIncludeExists() {
        let rules = [rule(0, .include, "calendar:/", definition: nil), rule(1, .include, "/test", definition: nil)]
        #expect(SourceRules.isEnabled(rules, scheme: "calendar"))
    }

    @Test("reports a source disabled when only an exclude rule exists for the scheme")
    func disabledWhenOnlyExclude() {
        let rules = [rule(0, .exclude, "calendar:/", definition: nil)]
        #expect(!SourceRules.isEnabled(rules, scheme: "calendar"))
    }

    @Test("reports a source disabled when no rule exists for the scheme")
    func disabledWhenNoRule() {
        let rules = [rule(0, .include, "contacts:/", definition: nil)]
        #expect(!SourceRules.isEnabled(rules, scheme: "calendar"))
    }

    @Test("matches all rules for the scheme regardless of operation")
    func matchesRegardlessOfOperation() {
        let rules = [
            rule(0, .include, "calendar:/", definition: nil),
            rule(1, .exclude, "calendar:/", definition: nil),
            rule(2, .include, "contacts:/", definition: nil),
            rule(3, .include, "/test", definition: nil)
        ]
        #expect(SourceRules.matching(rules, scheme: "calendar").map(\.id) == [0, 1])
    }

    @Test("reports all sets including the default")
    func allSetsIncludingDefault() {
        #expect(SourceRules.allSets(mixedRules) == Set<DatasetDefinitionId?>([nil, definitionA, definitionB]))
    }

    @Test("reports enabled sets for the scheme")
    func enabledSetsForScheme() {
        #expect(SourceRules.enabledSets(mixedRules, scheme: "calendar") == Set<DatasetDefinitionId?>([nil, definitionA]))
        #expect(SourceRules.enabledSets(mixedRules, scheme: "contacts") == Set<DatasetDefinitionId?>([definitionB]))
    }

    @Test("reports whether the scheme is in the default set")
    func inDefaultSet() {
        #expect(SourceRules.isInDefault(mixedRules, scheme: "calendar"))
        #expect(!SourceRules.isInDefault(mixedRules, scheme: "contacts"))
    }

    @Test("reports sets missing the scheme")
    func setsMissingScheme() {
        #expect(SourceRules.setsMissing(mixedRules, scheme: "calendar") == Set<DatasetDefinitionId?>([definitionB]))
        #expect(
            SourceRules.setsMissing(mixedRules, scheme: "contacts") == Set<DatasetDefinitionId?>([nil, definitionA])
        )
    }

    @Test("reports library schemes included in the default set")
    func defaultLibrarySchemes() {
        #expect(SourceRules.defaultLibrarySchemes(mixedRules) == Set(["calendar"]))
    }
}

import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("RecoverConfig")
struct RecoverConfigTests {
    @Test("initial config is missing a definition")
    func initialMissingDefinition() {
        #expect(RecoverConfig.initial.validate() == .missingDefinition)
    }

    @Test("definition + latest source is valid")
    func validWithLatestSource() {
        var config = RecoverConfig.initial
        config.definition = UUID()

        #expect(config.validate() == .valid)
    }

    @Test("entry source without an entry is missing entry")
    func entrySourceMissingEntry() {
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.recoverySource = .entry(nil)

        #expect(config.validate() == .missingEntry)
    }

    @Test("entry source with an entry is valid")
    func entrySourceValid() {
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.recoverySource = .entry(UUID())

        #expect(config.validate() == .valid)
    }

    @Test("until source is always valid when a definition is set")
    func untilSourceValid() {
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.recoverySource = .until(Date())

        #expect(config.validate() == .valid)
    }

    @Test("initial config selects all sources")
    func initialSelectsAllSources() {
        #expect(RecoverConfig.initial.sources == Set(RecoverConfig.allSources))
        #expect(!RecoverConfig.initial.sources.isEmpty)
    }

    @Test("empty sources is missing sources when a definition is set")
    func emptySourcesMissingSources() {
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.sources = []
        #expect(config.validate() == .missingSources)
    }

    @Test("missing definition takes precedence over empty sources")
    func missingDefinitionBeforeSources() {
        var config = RecoverConfig.initial
        config.sources = []
        #expect(config.validate() == .missingDefinition)
    }

    @Test("RecoverySource kind reflects the case")
    func sourceKindReflectsCase() {
        #expect(RecoverConfig.RecoverySource.latest.kind == .latest)
        #expect(RecoverConfig.RecoverySource.entry(nil).kind == .entry)
        #expect(RecoverConfig.RecoverySource.until(Date()).kind == .until)
    }

    @Test("ValidationResult buttonLabel reflects state")
    func buttonLabelReflectsState() {
        #expect(RecoverConfig.ValidationResult.valid.buttonLabel == "Run Recover")
        #expect(RecoverConfig.ValidationResult.missingDefinition.buttonLabel == "Pick a Definition")
        #expect(RecoverConfig.ValidationResult.missingEntry.buttonLabel == "Pick an Entry")
        #expect(RecoverConfig.ValidationResult.missingSources.buttonLabel == "Select Sources")
    }
}

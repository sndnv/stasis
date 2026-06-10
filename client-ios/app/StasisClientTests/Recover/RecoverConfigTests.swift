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

    @Test("recoveryPathQuery is nil for empty or whitespace-only input")
    func recoveryPathQueryNilForEmpty() {
        var config = RecoverConfig.initial
        config.pathQuery = ""
        #expect(config.recoveryPathQuery == nil)
        config.pathQuery = "   "
        #expect(config.recoveryPathQuery == nil)
    }

    @Test("recoveryPathQuery parses a non-empty input")
    func recoveryPathQueryParses() {
        var config = RecoverConfig.initial
        config.pathQuery = ".*\\.txt"
        #expect(config.recoveryPathQuery != nil)
    }

    @Test("recoveryDestination is nil for empty input")
    func recoveryDestinationNilForEmpty() {
        var config = RecoverConfig.initial
        config.destination = ""
        #expect(config.recoveryDestination == nil)
    }

    @Test("recoveryDestination builds with keepStructure inverse of discardPaths")
    func recoveryDestinationKeepStructureInverse() {
        var config = RecoverConfig.initial
        config.destination = "/tmp/recover"
        config.discardPaths = false
        #expect(config.recoveryDestination?.keepStructure == true)

        config.discardPaths = true
        #expect(config.recoveryDestination?.keepStructure == false)
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
    }
}

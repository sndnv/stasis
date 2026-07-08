import Foundation
@testable import StasisClient
import StasisClientLib
import SwiftData
import Testing

@Suite("RulesModel")
@MainActor
struct RulesModelTests {
    private func repository() throws -> RuleRepository {
        RuleRepository(modelContainer: try PersistenceSchema.inMemoryContainer())
    }

    private func source(_ permission: FakeLibrarySourcePermission) -> LibrarySource {
        LibrarySource(
            scheme: "photos",
            displayName: "Photos",
            systemImage: "photo",
            isExperimental: false,
            permission: permission
        )
    }

    @Test("enabling a granted source adds an include rule to the default set")
    func enableWhenGranted() async throws {
        let repo = try repository()
        let permission = FakeLibrarySourcePermission(status: .granted, grantOnRequest: true)
        let model = RulesModel(ruleRepository: repo, sources: [source(permission)])
        await model.start()

        await model.setEnabled("photos", true)

        let rules = try await repo.rules()
        #expect(rules.count == 1)
        #expect(rules[0].operation == .include)
        #expect(rules[0].source == "photos:/")
        #expect(rules[0].pattern == "*")
        #expect(rules[0].definition == nil)
        #expect(permission.requestCount == 0)
        #expect(model.rows.first?.isEnabled == true)
    }

    @Test("enabling does not duplicate a rule when the source is already enabled")
    func enableIsIdempotent() async throws {
        let repo = try repository()
        try await repo.put(Rule(id: 0, operation: .include, source: "photos:/", pattern: "*", definition: nil))
        let model = RulesModel(
            ruleRepository: repo,
            sources: [source(FakeLibrarySourcePermission(status: .granted, grantOnRequest: true))]
        )
        await model.start()

        await model.setEnabled("photos", true)

        #expect(try await repo.rules().count == 1)
    }

    @Test("enabling an undetermined source requests access, then adds the rule on grant")
    func enableRequestsThenApplies() async throws {
        let repo = try repository()
        let permission = FakeLibrarySourcePermission(status: .undetermined, grantOnRequest: true)
        let model = RulesModel(ruleRepository: repo, sources: [source(permission)])
        await model.start()

        await model.setEnabled("photos", true)

        #expect(permission.requestCount == 1)
        #expect(try await repo.rules().count == 1)
        #expect(model.rows.first?.isEnabled == true)
    }

    @Test("enabling an undetermined source that is refused adds nothing and prompts")
    func enableRequestsThenRefused() async throws {
        let repo = try repository()
        let permission = FakeLibrarySourcePermission(status: .undetermined, grantOnRequest: false)
        let model = RulesModel(ruleRepository: repo, sources: [source(permission)])
        await model.start()

        await model.setEnabled("photos", true)

        #expect(permission.requestCount == 1)
        #expect(try await repo.rules().isEmpty)
        #expect(model.permissionDenied?.scheme == "photos")
        #expect(model.rows.first?.isEnabled == false)
    }

    @Test("enabling a denied source adds nothing and prompts to open settings")
    func enableWhenDenied() async throws {
        let repo = try repository()
        let permission = FakeLibrarySourcePermission(status: .denied, grantOnRequest: false)
        let model = RulesModel(ruleRepository: repo, sources: [source(permission)])
        await model.start()

        await model.setEnabled("photos", true)

        #expect(permission.requestCount == 0)
        #expect(try await repo.rules().isEmpty)
        #expect(model.permissionDenied?.scheme == "photos")
    }

    @Test("disabling a source removes every matching rule")
    func disableRemovesRules() async throws {
        let repo = try repository()
        try await repo.put(Rule(id: 0, operation: .include, source: "photos:/", pattern: "*", definition: nil))
        try await repo.put(Rule(id: 0, operation: .exclude, source: "photos:/", pattern: "*.tmp", definition: nil))
        let model = RulesModel(
            ruleRepository: repo,
            sources: [source(FakeLibrarySourcePermission(status: .granted, grantOnRequest: true))]
        )
        await model.start()
        #expect(model.rows.first?.isEnabled == true)

        await model.setEnabled("photos", false)

        #expect(try await repo.rules().isEmpty)
        #expect(model.rows.first?.isEnabled == false)
    }

    @Test("resetting to defaults restores the default rules")
    func resetToDefaults() async throws {
        let repo = try repository()
        try await repo.put(Rule(id: 0, operation: .include, source: "contacts:/", pattern: "*", definition: nil))
        let model = RulesModel(
            ruleRepository: repo,
            sources: [source(FakeLibrarySourcePermission(status: .granted, grantOnRequest: true))]
        )
        await model.start()

        await model.resetToDefaults()

        #expect(try await repo.rules() == RulesConfig.defaultRules)
        #expect(model.rows.first?.isEnabled == true)
    }
}

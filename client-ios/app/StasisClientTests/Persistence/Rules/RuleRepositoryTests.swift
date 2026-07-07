import Foundation
@testable import StasisClient
import StasisClientLib
import SwiftData
import Testing

@Suite("RuleRepository")
struct RuleRepositoryTests {
    private func repository() throws -> RuleRepository {
        RuleRepository(modelContainer: try PersistenceSchema.inMemoryContainer())
    }

    @Test("persists, lists, and deletes rules")
    func crud() async throws {
        let repo = try repository()
        #expect(try await repo.rules().isEmpty)

        let definition = UUID()
        try await repo.put(Rule(
            id: 1, operation: .include, source: "/docs", pattern: "**", definition: definition
        ))
        try await repo.put(Rule(
            id: 2, operation: .exclude, source: "/docs", pattern: "tmp/**", definition: nil
        ))

        let stored = try await repo.rules()
        #expect(stored.count == 2)
        #expect(stored[0].definition == definition)
        #expect(stored[1].operation == .exclude)

        try await repo.delete(id: 1)
        #expect(try await repo.rules().map(\.id) == [2])

        try await repo.clear()
        #expect(try await repo.rules().isEmpty)
    }

    @Test("bootstrap seeds default rules")
    func bootstrap() async throws {
        let repo = try repository()
        try await repo.bootstrap()
        #expect(try await repo.rules().count == RulesConfig.defaultRules.count)
    }
}

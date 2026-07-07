import Foundation
import StasisClientLib
import SwiftData

@ModelActor
public actor RuleRepository {
    public func rules() throws -> [Rule] {
        let entities = try modelContext.fetch(FetchDescriptor<RuleEntity>())
        return try entities.map { try $0.toRule() }.sorted { $0.id < $1.id }
    }

    @discardableResult
    public func put(_ rule: Rule) throws -> Int64 {
        let entity = rule.toEntity()
        if entity.id == 0 {
            entity.id = try nextId()
            modelContext.insert(entity)
        } else {
            let targetId = entity.id
            let existing = try modelContext.fetch(
                FetchDescriptor<RuleEntity>(predicate: #Predicate { $0.id == targetId })
            ).first
            if let existing {
                existing.operationRaw = entity.operationRaw
                existing.source = entity.source
                existing.pattern = entity.pattern
                existing.definition = entity.definition
            } else {
                modelContext.insert(entity)
            }
        }
        try modelContext.save()
        return entity.id
    }

    private func nextId() throws -> Int64 {
        let existing = try modelContext.fetch(FetchDescriptor<RuleEntity>())
        return (existing.map(\.id).max() ?? 0) + 1
    }

    public func delete(id: Int64) throws {
        try modelContext.delete(
            model: RuleEntity.self,
            where: #Predicate { $0.id == id }
        )
        try modelContext.save()
    }

    public func bootstrap() throws {
        for rule in RulesConfig.defaultRules {
            modelContext.insert(rule.toEntity())
        }
        try modelContext.save()
    }

    public func clear() throws {
        try modelContext.delete(model: RuleEntity.self)
        try modelContext.save()
    }
}

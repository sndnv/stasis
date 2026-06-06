import Foundation
import StasisClientLib
import SwiftData

@ModelActor
public actor ActiveScheduleRepository {
    public func schedules() throws -> [ActiveSchedule] {
        let entities = try modelContext.fetch(FetchDescriptor<ActiveScheduleEntity>())
        return try entities.map { try $0.toActiveSchedule() }.sorted { $0.id < $1.id }
    }

    @discardableResult
    public func put(_ schedule: ActiveSchedule) throws -> Int64 {
        let entity = try schedule.toEntity()
        if entity.id == 0 {
            entity.id = try nextId()
        }
        modelContext.insert(entity)
        try modelContext.save()
        return entity.id
    }

    private func nextId() throws -> Int64 {
        let existing = try modelContext.fetch(FetchDescriptor<ActiveScheduleEntity>())
        return (existing.map(\.id).max() ?? 0) + 1
    }

    public func delete(id: Int64) throws {
        try modelContext.delete(
            model: ActiveScheduleEntity.self,
            where: #Predicate { $0.id == id }
        )
        try modelContext.save()
    }

    public func markFired(scheduleId: Int64, firedAt: Date) throws {
        let existing = try modelContext.fetch(
            FetchDescriptor<ActiveScheduleEntity>(predicate: #Predicate { $0.id == scheduleId })
        ).first
        guard let existing else { return }
        existing.lastFiredAt = firedAt
        try modelContext.save()
    }

    public func clear() throws {
        try modelContext.delete(model: ActiveScheduleEntity.self)
        try modelContext.save()
    }
}

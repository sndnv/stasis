import Foundation
import StasisClientLib
import SwiftData

@ModelActor
public actor LocalScheduleRepository {
    public func schedules() throws -> [Schedule] {
        let entities = try modelContext.fetch(FetchDescriptor<LocalScheduleEntity>())
        return entities.map { $0.toSchedule() }.sorted { $0.id.uuidString < $1.id.uuidString }
    }

    public func put(_ schedule: Schedule) throws {
        let targetId = schedule.id
        let existing = try modelContext.fetch(
            FetchDescriptor<LocalScheduleEntity>(predicate: #Predicate { $0.id == targetId })
        ).first
        if let existing {
            existing.info = schedule.info
            existing.start = schedule.start.value
            existing.intervalSeconds = schedule.interval.value
            existing.created = schedule.created
        } else {
            modelContext.insert(schedule.toLocalScheduleEntity())
        }
        try modelContext.save()
    }

    public func delete(id: ScheduleId) throws {
        try modelContext.delete(
            model: LocalScheduleEntity.self,
            where: #Predicate { $0.id == id }
        )
        try modelContext.save()
    }

    public func clear() throws {
        try modelContext.delete(model: LocalScheduleEntity.self)
        try modelContext.save()
    }
}

import Foundation
@testable import StasisClient
import StasisClientLib
import SwiftData
import Testing

@Suite("ActiveScheduleRepository")
struct ActiveScheduleRepositoryTests {
    private func repository() throws -> ActiveScheduleRepository {
        ActiveScheduleRepository(modelContainer: try PersistenceSchema.inMemoryContainer())
    }

    @Test("persists and deletes assignments")
    func crud() async throws {
        let repo = try repository()
        #expect(try await repo.schedules().isEmpty)

        let assignment: OperationScheduleAssignment = .backup(
            schedule: UUID(),
            definition: UUID(),
            entities: [URL(fileURLWithPath: "/tmp/x")]
        )
        let id = try await repo.put(ActiveSchedule(id: 11, assignment: assignment))

        let stored = try await repo.schedules()
        #expect(stored.count == 1)
        #expect(stored[0].assignment == assignment)

        try await repo.delete(id: id)
        #expect(try await repo.schedules().isEmpty)
    }

    @Test("clears every persisted assignment")
    func clear() async throws {
        let repo = try repository()
        try await repo.put(ActiveSchedule(id: 1, assignment: .expiration(schedule: UUID())))
        try await repo.put(ActiveSchedule(id: 2, assignment: .validation(schedule: UUID())))
        #expect(try await repo.schedules().count == 2)

        try await repo.clear()
        #expect(try await repo.schedules().isEmpty)
    }
}

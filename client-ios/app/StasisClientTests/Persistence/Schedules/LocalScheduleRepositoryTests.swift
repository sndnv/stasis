import Foundation
@testable import StasisClient
import StasisClientLib
import SwiftData
import Testing

@Suite("LocalScheduleRepository")
struct LocalScheduleRepositoryTests {
    private func repository() throws -> LocalScheduleRepository {
        LocalScheduleRepository(modelContainer: try PersistenceSchema.inMemoryContainer())
    }

    private func schedule(id: UUID = UUID(), info: String = "weekly") -> Schedule {
        Schedule(
            id: id,
            info: info,
            isPublic: false,
            start: LocalDateTime("2026-06-03T01:00:00"),
            interval: SecondsDuration(7 * 86_400),
            created: Date(timeIntervalSince1970: 1_700_000_000),
            updated: Date(timeIntervalSince1970: 1_700_000_000)
        )
    }

    @Test("round-trips a schedule")
    func roundTrip() async throws {
        let repo = try repository()
        let original = schedule()
        try await repo.put(original)

        let stored = try await repo.schedules()
        #expect(stored.count == 1)
        #expect(stored[0].id == original.id)
        #expect(stored[0].info == original.info)
    }

    @Test("deletes a schedule by id")
    func delete() async throws {
        let repo = try repository()
        let original = schedule()
        try await repo.put(original)

        try await repo.delete(id: original.id)
        #expect(try await repo.schedules().isEmpty)
    }

    @Test("clears every persisted schedule")
    func clear() async throws {
        let repo = try repository()
        try await repo.put(schedule(info: "a"))
        try await repo.put(schedule(info: "b"))
        #expect(try await repo.schedules().count == 2)

        try await repo.clear()
        #expect(try await repo.schedules().isEmpty)
    }
}

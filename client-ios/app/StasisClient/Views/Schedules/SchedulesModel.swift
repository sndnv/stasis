import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class SchedulesModel {
    struct Row: Identifiable, Equatable, Sendable {
        let id: ScheduleId
        let schedule: Schedule?
        let assignments: [ActiveSchedule]
        var isNext: Bool = false

        var nextInvocation: Date? { schedule?.nextInvocation() }
        var isLocal: Bool { schedule?.isPublic == false }
        var isOrphan: Bool { schedule == nil }

        var hasBackupAssignment: Bool {
            assignments.contains {
                if case .backup = $0.assignment { return true }
                return false
            }
        }
    }

    private let session: AuthenticatedSession
    private let scheduler: BackgroundScheduler
    private let localScheduleRepository: LocalScheduleRepository

    private(set) var rows: [Row] = []
    private(set) var definitions: [DatasetDefinition] = []
    private(set) var isLoading: Bool = true
    private(set) var error: String?

    private var schedulesSnapshot: Schedules = .empty

    init(
        session: AuthenticatedSession,
        scheduler: BackgroundScheduler,
        localScheduleRepository: LocalScheduleRepository
    ) {
        self.session = session
        self.scheduler = scheduler
        self.localScheduleRepository = localScheduleRepository
    }

    func clearError() { error = nil }

    func refresh() async { await load() }

    func start() async {
        await load()
        await observe()
    }

    func definitionInfo(_ id: DatasetDefinitionId) -> String? {
        definitions.first(where: { $0.id == id })?.info
    }

    func saveLocalSchedule(_ schedule: Schedule) async -> Bool {
        do {
            try await localScheduleRepository.put(schedule)
            await scheduler.refresh()
            await syncSnapshot()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func deleteLocalSchedule(_ id: ScheduleId) async {
        do {
            try await localScheduleRepository.delete(id: id)
            await scheduler.refresh()
            await syncSnapshot()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func addAssignment(_ activeSchedule: ActiveSchedule) async -> Bool {
        do {
            try await scheduler.add(activeSchedule: activeSchedule)
            await syncSnapshot()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func removeAssignment(_ id: Int64) async {
        do {
            try await scheduler.remove(scheduleId: id)
            await syncSnapshot()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func syncSnapshot() async {
        schedulesSnapshot = await scheduler.snapshot()
        recompute()
    }

    private func load() async {
        do {
            definitions = try await session.serverApiClient.datasetDefinitions()
        } catch {
            self.error = error.localizedDescription
            definitions = []
        }
        schedulesSnapshot = await scheduler.snapshot()
        recompute()
        isLoading = false
    }

    private func observe() async {
        for await update in await scheduler.updates() {
            schedulesSnapshot = update
            recompute()
        }
    }

    private func recompute() {
        let combined = schedulesSnapshot.publicSchedules + schedulesSnapshot.local
        let combinedById = Dictionary(combined.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let byId = Dictionary(grouping: schedulesSnapshot.configured) { $0.assignment.schedule }
        var allIds = Set(combined.map(\.id))
        allIds.formUnion(byId.keys)
        var ordered = allIds.map { id in
            Row(
                id: id,
                schedule: combinedById[id],
                assignments: byId[id] ?? []
            )
        }
        .sorted { lhs, rhs in
            switch (lhs.nextInvocation, rhs.nextInvocation) {
            case let (.some(left), .some(right)): return left < right
            case (.none, .some): return true
            case (.some, .none): return false
            case (.none, .none): return lhs.id.uuidString < rhs.id.uuidString
            }
        }
        if let nextIndex = ordered.firstIndex(where: { !$0.assignments.isEmpty && $0.nextInvocation != nil }) {
            ordered[nextIndex].isNext = true
        }
        rows = ordered
    }
}

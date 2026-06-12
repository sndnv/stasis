import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import SwiftData
import Testing

@MainActor
@Suite("SchedulesModel")
struct SchedulesModelTests {
    @Test("start populates definitions and rows from public, local and configured sources")
    func startPopulatesRows() async throws {
        let publicSchedule = futureSchedule()
        let localSchedule = futureSchedule()
        let definition = TestGenerators.definition(info: "Photos")
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsOverride([definition])
        let bundle = try await makeBundle(api: api, publicSchedules: [publicSchedule])
        try await bundle.localScheduleRepository.put(localSchedule)
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .backup(schedule: publicSchedule.id, definition: definition.id, entities: [])
        ))
        let model = bundle.model

        await model.refresh()

        #expect(model.definitions.map(\.id) == [definition.id])
        #expect(model.rows.count == 2)
        #expect(model.rows.contains(where: { $0.id == publicSchedule.id }))
        #expect(model.rows.contains(where: { $0.id == localSchedule.id }))
    }

    @Test("rows are sorted by next invocation, with orphans first")
    func rowsSortedByNextInvocation() async throws {
        let later = futureSchedule(inSeconds: 7200)
        let earlier = futureSchedule(inSeconds: 60)
        let orphanScheduleId = UUID()
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let bundle = try await makeBundle(api: api, publicSchedules: [later, earlier])
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: orphanScheduleId)
        ))
        let model = bundle.model

        await model.refresh()

        #expect(model.rows.first?.id == orphanScheduleId)
        let ordered = model.rows.dropFirst().map(\.id)
        #expect(ordered == [earlier.id, later.id])
    }

    @Test("isNext marks the earliest assigned schedule and skips unassigned earlier ones")
    func nextToFireSkipsUnassigned() async throws {
        let earlyUnassigned = futureSchedule(inSeconds: 60)
        let laterAssigned = futureSchedule(inSeconds: 3600)
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let bundle = try await makeBundle(api: api, publicSchedules: [earlyUnassigned, laterAssigned])
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: laterAssigned.id)
        ))
        let model = bundle.model

        await model.refresh()

        let next = try #require(model.rows.first(where: { $0.isNext }))
        #expect(next.id == laterAssigned.id)
        #expect(model.rows.filter(\.isNext).count == 1)
    }

    @Test("isNext picks the earliest invocation among schedules with assignments")
    func nextToFirePicksEarliestAssigned() async throws {
        let early = futureSchedule(inSeconds: 60)
        let later = futureSchedule(inSeconds: 3600)
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let bundle = try await makeBundle(api: api, publicSchedules: [later, early])
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 1,
            assignment: .expiration(schedule: early.id)
        ))
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 2,
            assignment: .expiration(schedule: later.id)
        ))
        let model = bundle.model

        await model.refresh()

        let next = try #require(model.rows.first(where: { $0.isNext }))
        #expect(next.id == early.id)
    }

    @Test("orphan active schedules produce rows with a nil schedule")
    func orphansHaveNilSchedule() async throws {
        let orphanId = UUID()
        let bundle = try await makeBundle()
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .validation(schedule: orphanId)
        ))
        let model = bundle.model

        await model.refresh()

        let orphan = try #require(model.rows.first(where: { $0.id == orphanId }))
        #expect(orphan.schedule == nil)
        #expect(orphan.isOrphan == true)
        #expect(orphan.assignments.count == 1)
    }

    @Test("definitionInfo returns the matching info string")
    func definitionInfoLookup() async throws {
        let definition = TestGenerators.definition(info: "Documents")
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsOverride([definition])
        let bundle = try await makeBundle(api: api)
        let model = bundle.model
        await model.refresh()

        #expect(model.definitionInfo(definition.id) == "Documents")
        #expect(model.definitionInfo(UUID()) == nil)
    }

    @Test("refresh still populates rows when datasetDefinitions fails")
    func refreshHandlesDefinitionsError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let bundle = try await makeBundle(api: api, publicSchedules: [futureSchedule()])
        let model = bundle.model

        await model.refresh()

        #expect(model.error != nil)
        #expect(model.definitions.isEmpty)
        #expect(model.rows.count == 1)
    }

    @Test("saveLocalSchedule persists the schedule and refreshes rows")
    func saveLocalSchedulePersists() async throws {
        let bundle = try await makeBundle()
        let model = bundle.model
        await model.refresh()
        let schedule = futureSchedule(isPublic: false)

        let success = await model.saveLocalSchedule(schedule)
        await model.refresh()

        #expect(success == true)
        #expect(model.rows.contains(where: { $0.id == schedule.id }))
        let stored = try await bundle.localScheduleRepository.schedules()
        #expect(stored.map(\.id).contains(schedule.id))
    }

    @Test("deleteLocalSchedule removes the schedule and refreshes rows")
    func deleteLocalScheduleRemoves() async throws {
        let bundle = try await makeBundle()
        let schedule = futureSchedule(isPublic: false)
        try await bundle.localScheduleRepository.put(schedule)
        await bundle.scheduler.refresh()
        let model = bundle.model
        await model.refresh()
        #expect(model.rows.contains(where: { $0.id == schedule.id }))

        await model.deleteLocalSchedule(schedule.id)
        await model.refresh()

        #expect(model.rows.contains(where: { $0.id == schedule.id }) == false)
        let stored = try await bundle.localScheduleRepository.schedules()
        #expect(stored.isEmpty)
    }

    @Test("addAssignment forwards to the scheduler and persists the active schedule")
    func addAssignmentForwards() async throws {
        let schedule = futureSchedule()
        let bundle = try await makeBundle(publicSchedules: [schedule])
        let model = bundle.model
        await model.refresh()

        let success = await model.addAssignment(ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: schedule.id)
        ))

        #expect(success == true)
        let stored = try await bundle.activeScheduleRepository.schedules()
        #expect(stored.count == 1)
    }

    @Test("removeAssignment removes the persisted active schedule")
    func removeAssignmentRemoves() async throws {
        let schedule = futureSchedule()
        let bundle = try await makeBundle(publicSchedules: [schedule])
        let id = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .validation(schedule: schedule.id)
        ))
        let model = bundle.model
        await model.refresh()

        await model.removeAssignment(id)

        let stored = try await bundle.activeScheduleRepository.schedules()
        #expect(stored.isEmpty)
    }

    @Test("Row.hasBackupAssignment is true only when a backup assignment exists")
    func rowHasBackupAssignment() async throws {
        let scheduleId = UUID()
        let backup = ActiveSchedule(
            id: 1,
            assignment: .backup(schedule: scheduleId, definition: UUID(), entities: [])
        )
        let expiration = ActiveSchedule(id: 2, assignment: .expiration(schedule: scheduleId))

        let withBackup = SchedulesModel.Row(id: scheduleId, schedule: nil, assignments: [backup])
        let withoutBackup = SchedulesModel.Row(
            id: scheduleId, schedule: nil, assignments: [expiration]
        )
        let empty = SchedulesModel.Row(id: scheduleId, schedule: nil, assignments: [])

        #expect(withBackup.hasBackupAssignment == true)
        #expect(withoutBackup.hasBackupAssignment == false)
        #expect(empty.hasBackupAssignment == false)
    }

    @Test("removeAssignment on the only assignment of an orphan removes the row")
    func removeAssignmentOnOrphanRemovesRow() async throws {
        let orphanScheduleId = UUID()
        let bundle = try await makeBundle()
        let id = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .validation(schedule: orphanScheduleId)
        ))
        let model = bundle.model
        await model.refresh()
        #expect(model.rows.contains(where: { $0.id == orphanScheduleId }))

        await model.removeAssignment(id)

        #expect(model.rows.contains(where: { $0.id == orphanScheduleId }) == false)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let bundle = try await makeBundle(api: api)
        let model = bundle.model
        await model.refresh()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }

    private struct Bundle {
        let scheduler: BackgroundScheduler
        let activeScheduleRepository: ActiveScheduleRepository
        let localScheduleRepository: LocalScheduleRepository
        let model: SchedulesModel
    }

    private func makeBundle(
        api: StasisClientLibTestSupport.MockServerApiEndpointClient =
            StasisClientLibTestSupport.MockServerApiEndpointClient(),
        publicSchedules: [Schedule] = []
    ) async throws -> Bundle {
        let container = try PersistenceSchema.inMemoryContainer()
        let activeRepo = ActiveScheduleRepository(modelContainer: container)
        let localRepo = LocalScheduleRepository(modelContainer: container)
        let ruleRepo = RuleRepository(modelContainer: container)
        let scheduler = BackgroundScheduler(
            activeScheduleRepository: activeRepo,
            localScheduleRepository: localRepo,
            ruleRepository: ruleRepo,
            executor: MockOperationExecutor(),
            notifications: MockSchedulingNotifications(),
            publicSchedulesLoader: { publicSchedules },
            taskScheduler: MockBackgroundTaskScheduler()
        )
        await scheduler.refresh()
        let session = try TestSession.make(api: api)
        let model = SchedulesModel(
            session: session,
            scheduler: scheduler,
            localScheduleRepository: localRepo
        )
        return Bundle(
            scheduler: scheduler,
            activeScheduleRepository: activeRepo,
            localScheduleRepository: localRepo,
            model: model
        )
    }

    private func futureSchedule(
        id: ScheduleId = UUID(),
        isPublic: Bool = true,
        inSeconds: TimeInterval = 3600
    ) -> Schedule {
        let start = formatLocalDateTime(Date().addingTimeInterval(inSeconds))
        return Schedule(
            id: id,
            info: "test",
            isPublic: isPublic,
            start: LocalDateTime(start),
            interval: SecondsDuration(3600),
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0)
        )
    }

    private func formatLocalDateTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        return formatter.string(from: date)
    }
}

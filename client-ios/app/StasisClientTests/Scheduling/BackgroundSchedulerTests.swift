import Foundation
@testable import StasisClient
import StasisClientLib
import SwiftData
import Testing

@Suite("BackgroundScheduler")
struct BackgroundSchedulerTests {
    @Test("add persists the active schedule and requests notification authorization once")
    func addRequestsAuthorizationOnce() async throws {
        let bundle = try makeBundle()
        let assignment: OperationScheduleAssignment = .expiration(schedule: UUID())

        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(id: 0, assignment: assignment))
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(id: 0, assignment: assignment))

        #expect(await bundle.notifications.authorizationRequests == 1)
        let stored = try await bundle.activeScheduleRepository.schedules()
        #expect(stored.count == 2)
    }

    @Test("remove deletes the active schedule and refreshes state")
    func removeDeletesAndRefreshes() async throws {
        let bundle = try makeBundle()
        let assignment: OperationScheduleAssignment = .validation(schedule: UUID())
        let id = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(id: 0, assignment: assignment))
        #expect(await bundle.scheduler.snapshot().configured.count == 1)

        try await bundle.scheduler.remove(scheduleId: id)
        #expect(await bundle.scheduler.snapshot().configured.isEmpty)
    }

    @Test("refresh populates state from public, local and configured sources")
    func refreshPopulatesState() async throws {
        let publicSchedule = futureSchedule(id: UUID())
        let localSchedule = futureSchedule(id: UUID())
        let bundle = try makeBundle(publicSchedules: [publicSchedule])
        try await bundle.localScheduleRepository.put(localSchedule)
        let assignment: OperationScheduleAssignment = .backup(
            schedule: publicSchedule.id,
            definition: UUID(),
            entities: []
        )
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(id: 0, assignment: assignment))

        let snapshot = await bundle.scheduler.snapshot()
        #expect(snapshot.publicSchedules.map(\.id) == [publicSchedule.id])
        #expect(snapshot.local.map(\.id) == [localSchedule.id])
        #expect(snapshot.configured.count == 1)
    }

    @Test("refresh submits a BG processing task for the earliest active schedule")
    func refreshSubmitsProcessingTask() async throws {
        let schedule = futureSchedule(id: UUID(), inSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule])

        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: schedule.id)
        ))

        let submission = try #require(bundle.taskScheduler.submissions.last)
        #expect(submission.identifier == BackgroundScheduler.processingTaskIdentifier)
        #expect(submission.requiresNetwork)
    }

    @Test("refresh cancels the BG task when there are no active schedules")
    func refreshCancelsWhenNoSchedules() async throws {
        let bundle = try makeBundle()
        await bundle.scheduler.refresh()
        #expect(bundle.taskScheduler.cancellations.contains(BackgroundScheduler.processingTaskIdentifier))
    }

    @Test("refresh submits no BG task and cancels when scheduling is disabled")
    func refreshSkipsSubmissionWhenSchedulingDisabled() async throws {
        let schedule = futureSchedule(id: UUID(), inSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule], schedulingEnabled: { false })

        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: schedule.id)
        ))

        #expect(bundle.taskScheduler.submissions.isEmpty)
        #expect(bundle.taskScheduler.cancellations.contains(BackgroundScheduler.processingTaskIdentifier))
    }

    @Test("executeReady fires nothing and cancels when scheduling is disabled")
    func executeReadySkipsOperationsWhenSchedulingDisabled() async throws {
        let schedule = pastSchedule(id: UUID(), agoSeconds: 120, intervalSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule], schedulingEnabled: { false })
        let assignment: OperationScheduleAssignment = .expiration(schedule: schedule.id)
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(id: 0, assignment: assignment))

        await bundle.scheduler.executeReady()

        #expect(await bundle.executor.calls.isEmpty)
        #expect(await bundle.notifications.operationStarted.isEmpty)
        #expect(bundle.taskScheduler.cancellations.contains(BackgroundScheduler.processingTaskIdentifier))
    }

    @Test("executeReady fires the operation for ready schedules and posts notifications")
    func executeReadyFiresReadyOperations() async throws {
        let schedule = pastSchedule(id: UUID(), agoSeconds: 120, intervalSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule])
        let assignment: OperationScheduleAssignment = .expiration(schedule: schedule.id)
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(id: 0, assignment: assignment))

        await bundle.scheduler.executeReady()

        #expect(await bundle.executor.calls.contains(.expiration))
        #expect(await bundle.notifications.operationStarted.count == 1)
        await eventually { await bundle.notifications.operationCompleted.count == 1 }
    }

    @Test("executeReady fires only once per invocation across multiple calls")
    func executeReadyFiresOnlyOncePerInvocation() async throws {
        let schedule = pastSchedule(id: UUID(), agoSeconds: 120, intervalSeconds: 86_400)
        let bundle = try makeBundle(publicSchedules: [schedule])
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: schedule.id)
        ))

        await bundle.scheduler.executeReady()
        await bundle.scheduler.executeReady()

        let count = await bundle.executor.calls.filter { $0 == .expiration }.count
        #expect(count == 1)
    }

    @Test("submitProcessing errors do not propagate or crash refresh")
    func submitFailureIsAbsorbed() async throws {
        let schedule = futureSchedule(id: UUID(), inSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule])
        bundle.taskScheduler.setNextSubmitError(BundleTestError.submit)

        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: schedule.id)
        ))

        #expect(await bundle.scheduler.snapshot().configured.count == 1)
    }

    @Test("expiration/validation/keyRotation throws surface as failure notifications")
    func executorThrowsBecomesFailedNotification() async throws {
        let schedule = pastSchedule(id: UUID(), agoSeconds: 120, intervalSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule])
        await bundle.executor.setStartExpirationError(BundleTestError.expiration)

        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: schedule.id)
        ))

        await bundle.scheduler.executeReady()

        let completions = await bundle.notifications.operationCompleted
        #expect(completions.count == 1)
        #expect(completions.first?.failure != nil)
    }

    @Test("scheduleNextProcessingTask clamps earliestBeginDate to minimumExecutionDelay")
    func submissionClampedToMinimumDelay() async throws {
        let schedule = pastSchedule(id: UUID(), agoSeconds: 0, intervalSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule])
        let before = Date()
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: schedule.id)
        ))

        let last = try #require(bundle.taskScheduler.submissions.last)
        let elapsed = last.earliestBeginDate.timeIntervalSince(before)
        #expect(elapsed >= BackgroundScheduler.minimumExecutionDelay)
    }

    @Test("executeReady skips schedules that are not yet due")
    func executeReadySkipsNotDue() async throws {
        let schedule = futureSchedule(id: UUID(), inSeconds: 3600)
        let bundle = try makeBundle(publicSchedules: [schedule])
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .validation(schedule: schedule.id)
        ))

        await bundle.scheduler.executeReady()

        #expect(await bundle.executor.calls.isEmpty)
        #expect(await bundle.notifications.operationStarted.isEmpty)
    }

    @Test("executeReady posts public-schedule-not-found when matching schedule is missing")
    func executeReadyNotifiesMissingPublicSchedule() async throws {
        let bundle = try makeBundle(publicSchedules: [])
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .keyRotation(schedule: UUID())
        ))

        await bundle.scheduler.executeReady()

        #expect(await bundle.notifications.publicScheduleNotFound.count == 1)
        #expect(await bundle.executor.calls.isEmpty)
    }

    @Test("backup with rules picks up rules from the repository")
    func executeReadyBackupWithRules() async throws {
        let schedule = pastSchedule(id: UUID(), agoSeconds: 120, intervalSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule])
        try await bundle.ruleRepository.put(Rule(
            id: 0, operation: .include, source: "/tmp", pattern: "*", definition: nil
        ))
        let definitionId = UUID()
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .backup(schedule: schedule.id, definition: definitionId, entities: [])
        ))

        await bundle.scheduler.executeReady()

        #expect(await bundle.executor.calls.contains(.backupRules(definition: definitionId, rules: 1)))
    }

    @Test("backup with entities passes them straight through")
    func executeReadyBackupWithEntities() async throws {
        let schedule = pastSchedule(id: UUID(), agoSeconds: 120, intervalSeconds: 60)
        let bundle = try makeBundle(publicSchedules: [schedule])
        let entities = [URL(fileURLWithPath: "/tmp/a"), URL(fileURLWithPath: "/tmp/b")]
        let definitionId = UUID()
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .backup(schedule: schedule.id, definition: definitionId, entities: entities)
        ))

        await bundle.scheduler.executeReady()

        #expect(await bundle.executor.calls.contains(.backupEntities(definition: definitionId, entities: entities)))
    }

    @Test("updates stream emits state changes")
    func updatesEmitsChanges() async throws {
        let bundle = try makeBundle()
        let stream = await bundle.scheduler.updates()
        _ = try await bundle.scheduler.add(activeSchedule: ActiveSchedule(
            id: 0,
            assignment: .expiration(schedule: UUID())
        ))

        var observed: Schedules?
        for await snapshot in stream where !snapshot.configured.isEmpty {
            observed = snapshot
            break
        }
        let snapshot = try #require(observed)
        #expect(snapshot.configured.count == 1)
    }

    @Test("stop cancels the BG task and finishes subscribers")
    func stopCancelsAndFinishes() async throws {
        let bundle = try makeBundle()
        await bundle.scheduler.stop()
        #expect(bundle.taskScheduler.cancellations.contains(BackgroundScheduler.processingTaskIdentifier))
    }

    // MARK: - Helpers

    private struct Bundle {
        let scheduler: BackgroundScheduler
        let executor: MockOperationExecutor
        let notifications: MockSchedulingNotifications
        let taskScheduler: MockBackgroundTaskScheduler
        let activeScheduleRepository: ActiveScheduleRepository
        let localScheduleRepository: LocalScheduleRepository
        let ruleRepository: RuleRepository
    }

    private func makeBundle(
        publicSchedules: [Schedule] = [],
        schedulingEnabled: @escaping @Sendable () -> Bool = { true }
    ) throws -> Bundle {
        let container = try PersistenceSchema.inMemoryContainer()
        let activeRepo = ActiveScheduleRepository(modelContainer: container)
        let localRepo = LocalScheduleRepository(modelContainer: container)
        let ruleRepo = RuleRepository(modelContainer: container)
        let executor = MockOperationExecutor()
        let notifications = MockSchedulingNotifications()
        let taskScheduler = MockBackgroundTaskScheduler()
        let scheduler = BackgroundScheduler(
            activeScheduleRepository: activeRepo,
            localScheduleRepository: localRepo,
            ruleRepository: ruleRepo,
            executor: executor,
            notifications: notifications,
            schedulingEnabled: schedulingEnabled,
            publicSchedulesLoader: { publicSchedules },
            taskScheduler: taskScheduler
        )
        return Bundle(
            scheduler: scheduler,
            executor: executor,
            notifications: notifications,
            taskScheduler: taskScheduler,
            activeScheduleRepository: activeRepo,
            localScheduleRepository: localRepo,
            ruleRepository: ruleRepo
        )
    }

    private func futureSchedule(id: ScheduleId, inSeconds: TimeInterval = 3600) -> Schedule {
        let start = formatLocalDateTime(Date().addingTimeInterval(inSeconds))
        return Schedule(
            id: id, info: "test", isPublic: true,
            start: LocalDateTime(start), interval: SecondsDuration(3600),
            created: Date(timeIntervalSince1970: 0), updated: Date(timeIntervalSince1970: 0)
        )
    }

    private func pastSchedule(id: ScheduleId, agoSeconds: TimeInterval, intervalSeconds: Int64) -> Schedule {
        let start = formatLocalDateTime(Date().addingTimeInterval(-agoSeconds))
        return Schedule(
            id: id, info: "test", isPublic: true,
            start: LocalDateTime(start), interval: SecondsDuration(intervalSeconds),
            created: Date(timeIntervalSince1970: 0), updated: Date(timeIntervalSince1970: 0)
        )
    }

    private enum BundleTestError: Error, Equatable { case submit, expiration }

    private func formatLocalDateTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        return formatter.string(from: date)
    }
}

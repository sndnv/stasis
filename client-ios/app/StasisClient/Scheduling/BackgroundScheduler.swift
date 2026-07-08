import Foundation
import OSLog
import StasisClientLib

public actor BackgroundScheduler {
    public static let processingTaskIdentifier: String = "stasis.client.ios.processing"
    public static let minimumExecutionDelay: TimeInterval = 5

    private static let logger = Logger(subsystem: "stasis.client.ios", category: "BackgroundScheduler")

    private let activeScheduleRepository: ActiveScheduleRepository
    private let localScheduleRepository: LocalScheduleRepository
    private let ruleRepository: RuleRepository
    private let executor: any OperationExecutor
    private let notifications: any SchedulingNotifications
    private let schedulingEnabled: @Sendable () -> Bool
    private let publicSchedulesCache: RefreshingCache<Int, [Schedule]>
    public let publicSchedulesTracking: TrackingCache<Int, [Schedule]>
    private var publicSchedulesLoader: @Sendable () async throws -> [Schedule]
    private let taskScheduler: any BackgroundTaskScheduling

    private var state: Schedules = .empty
    private var subscribers: [UUID: AsyncStream<Schedules>.Continuation] = [:]
    private var hasRequestedNotificationAuthorization: Bool = false
    private var executionTask: Task<Void, Never>?

    public init(
        activeScheduleRepository: ActiveScheduleRepository,
        localScheduleRepository: LocalScheduleRepository,
        ruleRepository: RuleRepository,
        executor: any OperationExecutor,
        notifications: any SchedulingNotifications,
        schedulingEnabled: @escaping @Sendable () -> Bool,
        publicSchedulesLoader: @escaping @Sendable () async throws -> [Schedule] = { [] },
        publicSchedulesRefreshInterval: TimeInterval = 30 * 60,
        taskScheduler: any BackgroundTaskScheduling = SystemBackgroundTaskScheduler()
    ) {
        self.activeScheduleRepository = activeScheduleRepository
        self.localScheduleRepository = localScheduleRepository
        self.ruleRepository = ruleRepository
        self.executor = executor
        self.notifications = notifications
        self.schedulingEnabled = schedulingEnabled
        self.publicSchedulesLoader = publicSchedulesLoader
        let tracking = TrackingCache<Int, [Schedule]>(underlying: MapCache())
        self.publicSchedulesTracking = tracking
        self.publicSchedulesCache = RefreshingCache(
            underlying: tracking,
            interval: publicSchedulesRefreshInterval
        )
        self.taskScheduler = taskScheduler
    }

    public func start() async {
        let configured = (try? await activeScheduleRepository.schedules()) ?? []
        if !configured.isEmpty {
            await ensureAuthorizationRequested()
        }
        await refresh()
    }

    public func stop() {
        taskScheduler.cancel(identifier: Self.processingTaskIdentifier)
        executionTask?.cancel()
        executionTask = nil
        for continuation in subscribers.values { continuation.finish() }
        subscribers.removeAll()
    }

    @discardableResult
    public func add(activeSchedule: ActiveSchedule) async throws -> Int64 {
        let id = try await activeScheduleRepository.put(activeSchedule)
        await ensureAuthorizationRequested()
        await refresh()
        return id
    }

    private func ensureAuthorizationRequested() async {
        guard !hasRequestedNotificationAuthorization else { return }
        hasRequestedNotificationAuthorization = true
        _ = await notifications.requestAuthorization()
    }

    public func remove(scheduleId: Int64) async throws {
        try await activeScheduleRepository.delete(id: scheduleId)
        await refresh()
    }

    public func refresh() async {
        let snapshot = await loadAll()
        state = snapshot
        broadcast()
        scheduleNextProcessingTask(from: snapshot)
    }

    public func setPublicSchedulesLoader(_ loader: @escaping @Sendable () async throws -> [Schedule]) async {
        publicSchedulesLoader = loader
        try? await publicSchedulesCache.remove(0)
        await refresh()
    }

    public func executeReady(now: Date = Date()) async {
        let task: Task<Void, Never> = Task { [weak self] in
            await self?.runExecution(now: now)
        }
        executionTask = task
        await task.value
        executionTask = nil
    }

    private func runExecution(now: Date) async {
        guard schedulingEnabled() else {
            taskScheduler.cancel(identifier: Self.processingTaskIdentifier)
            return
        }
        let snapshot = await loadAll()
        state = snapshot
        broadcast()
        let combined = snapshot.publicSchedules + snapshot.local
        for active in snapshot.configured {
            if Task.isCancelled { break }
            guard let schedule = combined.first(where: { $0.id == active.assignment.schedule }) else {
                await notifications.notifyPublicScheduleNotFound(activeSchedule: active)
                continue
            }
            guard let lastFire = schedule.lastInvocation(now: now) else { continue }
            if let already = active.lastFiredAt, already >= lastFire { continue }
            await execute(active: active, firedAt: lastFire)
        }
        let refreshed = await loadAll()
        state = refreshed
        broadcast()
        scheduleNextProcessingTask(from: refreshed)
    }

    public func snapshot() -> Schedules { state }

    public func updates() -> AsyncStream<Schedules> { subscribe() }

    private func loadAll() async -> Schedules {
        async let publicLoad = loadPublicSchedules()
        let local = (try? await localScheduleRepository.schedules()) ?? []
        let configured = (try? await activeScheduleRepository.schedules()) ?? []
        let publicSchedules = (try? await publicLoad) ?? []
        return Schedules(publicSchedules: publicSchedules, local: local, configured: configured)
    }

    private func loadPublicSchedules() async throws -> [Schedule] {
        let loader = publicSchedulesLoader
        return try await publicSchedulesCache.getOrLoad(0) { _ in try await loader() } ?? []
    }

    private func execute(active: ActiveSchedule, firedAt: Date) async {
        await notifications.notifyOperationStarted(activeSchedule: active)
        try? await activeScheduleRepository.markFired(scheduleId: active.id, firedAt: firedAt)
        let onComplete: OperationCallback = { [notifications, active] error in
            Task { await notifications.notifyOperationCompleted(activeSchedule: active, failure: error) }
        }
        do {
            switch active.assignment {
            case .backup(_, let definition, let entities):
                if entities.isEmpty {
                    let rules = (try? await ruleRepository.rules()) ?? []
                    _ = await executor.startBackupWithRules(definition: definition, rules: rules, callback: onComplete)
                } else {
                    _ = await executor.startBackupWithEntities(
                        definition: definition,
                        entities: entities,
                        callback: onComplete
                    )
                }
            case .expiration:
                _ = try await executor.startExpiration(callback: onComplete)
            case .validation:
                _ = try await executor.startValidation(callback: onComplete)
            case .keyRotation:
                _ = try await executor.startKeyRotation(callback: onComplete)
            }
        } catch {
            await notifications.notifyOperationCompleted(activeSchedule: active, failure: error)
        }
    }

    private func scheduleNextProcessingTask(from snapshot: Schedules) {
        guard schedulingEnabled() else {
            taskScheduler.cancel(identifier: Self.processingTaskIdentifier)
            return
        }
        let now = Date()
        let combined = snapshot.publicSchedules + snapshot.local
        let nextFires: [Date] = snapshot.configured.compactMap { active in
            guard let schedule = combined.first(where: { $0.id == active.assignment.schedule }) else { return nil }
            return schedule.nextInvocation(now: now)
        }
        guard let earliest = nextFires.min() else {
            taskScheduler.cancel(identifier: Self.processingTaskIdentifier)
            return
        }
        let beginDate = max(earliest, now.addingTimeInterval(Self.minimumExecutionDelay))
        do {
            try taskScheduler.submitProcessing(
                identifier: Self.processingTaskIdentifier,
                earliestBeginDate: beginDate,
                requiresNetwork: true
            )
        } catch {
            Self.logger.error("failed to submit BG processing task: \(error.localizedDescription)")
        }
    }

    private func subscribe() -> AsyncStream<Schedules> {
        let id = UUID()
        return AsyncStream { continuation in
            subscribers[id] = continuation
            continuation.yield(state)
            continuation.onTermination = { [weak self] _ in
                Task { await self?.unsubscribe(id) }
            }
        }
    }

    private func unsubscribe(_ id: UUID) {
        subscribers.removeValue(forKey: id)
    }

    private func broadcast() {
        for continuation in subscribers.values {
            continuation.yield(state)
        }
    }
}

import Foundation
import Observation
import StasisClientLib
import SwiftData

@Observable
@MainActor
final class AppContainer {
    let appInfo: StasisApplicationInformation

    let settings: UserDefaults
    let configRepository: ConfigRepository
    let credentialsKeychain: Keychain

    let modelContainer: ModelContainer
    let ruleRepository: RuleRepository
    let activeScheduleRepository: ActiveScheduleRepository
    let localScheduleRepository: LocalScheduleRepository

    let trackers: DefaultTrackers
    let analyticsPersistence: DefaultAnalyticsPersistence
    let analyticsCollector: DefaultAnalyticsCollector

    let schedulingNotifications: any SchedulingNotifications
    let backgroundScheduler: BackgroundScheduler

    init(
        appInfo: StasisApplicationInformation = StasisApplicationInformation(),
        settings: UserDefaults = .standard,
        configRepository: ConfigRepository? = nil,
        credentialsKeychain: Keychain = Keychain(service: KeychainCredentialsStore.defaultService),
        modelContainer: ModelContainer? = nil,
        backupStateStore: StateStore<[OperationId: BackupState]>? = nil,
        recoveryStateStore: StateStore<[OperationId: RecoveryState]>? = nil,
        analyticsClient: @escaping @Sendable () -> any AnalyticsClient = { NoOpAnalyticsClient() },
        operationExecutor: any OperationExecutor = NoOpOperationExecutor(),
        publicSchedulesLoader: @escaping @Sendable () async throws -> [Schedule] = { [] },
        schedulingNotifications: (any SchedulingNotifications)? = nil,
        backgroundTaskScheduler: (any BackgroundTaskScheduling)? = nil
    ) {
        self.appInfo = appInfo
        self.settings = settings
        self.configRepository = configRepository ?? Self.makeConfigRepository()
        self.credentialsKeychain = credentialsKeychain

        let container = modelContainer ?? Self.makeModelContainer()
        self.modelContainer = container
        self.ruleRepository = Self.makeRuleRepository(modelContainer: container)
        self.activeScheduleRepository = Self.makeActiveScheduleRepository(modelContainer: container)
        self.localScheduleRepository = Self.makeLocalScheduleRepository(modelContainer: container)

        self.trackers = Self.makeTrackers(
            backupStore: backupStateStore,
            recoveryStore: recoveryStateStore
        )

        let persistence = Self.makeAnalyticsPersistence(
            preferences: self.configRepository.preferencesStore,
            client: analyticsClient
        )
        self.analyticsPersistence = persistence
        self.analyticsCollector = Self.makeAnalyticsCollector(
            app: appInfo,
            settings: settings,
            persistence: persistence
        )

        let notifications = schedulingNotifications ?? Self.makeSchedulingNotifications()
        self.schedulingNotifications = notifications
        self.backgroundScheduler = Self.makeBackgroundScheduler(
            activeScheduleRepository: self.activeScheduleRepository,
            localScheduleRepository: self.localScheduleRepository,
            ruleRepository: self.ruleRepository,
            executor: operationExecutor,
            notifications: notifications,
            publicSchedulesLoader: publicSchedulesLoader,
            taskScheduler: backgroundTaskScheduler ?? Self.makeBackgroundTaskScheduler()
        )
    }

    private static func makeConfigRepository() -> ConfigRepository {
        guard let repository = ConfigRepository() else {
            preconditionFailure("failed to open ConfigRepository for App Group \(ConfigRepository.suiteName)")
        }
        return repository
    }

    private static func makeModelContainer() -> ModelContainer {
        do {
            return try PersistenceSchema.defaultContainer()
        } catch {
            preconditionFailure("failed to create SwiftData container: \(error)")
        }
    }

    private static func makeRuleRepository(modelContainer: ModelContainer) -> RuleRepository {
        RuleRepository(modelContainer: modelContainer)
    }

    private static func makeActiveScheduleRepository(modelContainer: ModelContainer) -> ActiveScheduleRepository {
        ActiveScheduleRepository(modelContainer: modelContainer)
    }

    private static func makeLocalScheduleRepository(modelContainer: ModelContainer) -> LocalScheduleRepository {
        LocalScheduleRepository(modelContainer: modelContainer)
    }

    private static func makeTrackers(
        backupStore: StateStore<[OperationId: BackupState]>?,
        recoveryStore: StateStore<[OperationId: RecoveryState]>?
    ) -> DefaultTrackers {
        let backup: StateStore<[OperationId: BackupState]>
        let recovery: StateStore<[OperationId: RecoveryState]>
        do {
            backup = try backupStore ?? StateStores.backups()
            recovery = try recoveryStore ?? StateStores.recoveries()
        } catch {
            preconditionFailure("failed to create state stores: \(error)")
        }
        return DefaultTrackers(
            backup: DefaultBackupTracker(store: backup),
            recovery: DefaultRecoveryTracker(store: recovery),
            server: DefaultServerTracker()
        )
    }

    private static func makeAnalyticsPersistence(
        preferences: UserDefaults,
        client: @escaping @Sendable () -> any AnalyticsClient
    ) -> DefaultAnalyticsPersistence {
        DefaultAnalyticsPersistence(preferences: preferences, client: client)
    }

    private static func makeAnalyticsCollector(
        app: StasisApplicationInformation,
        settings: UserDefaults,
        persistence: DefaultAnalyticsPersistence
    ) -> DefaultAnalyticsCollector {
        DefaultAnalyticsCollector(
            app: app,
            persistenceInterval: settings.analyticsPersistenceInterval(),
            transmissionInterval: settings.analyticsTransmissionInterval(),
            persistence: persistence
        )
    }

    private static func makeSchedulingNotifications() -> any SchedulingNotifications {
        DefaultSchedulingNotifications()
    }

    private static func makeBackgroundTaskScheduler() -> any BackgroundTaskScheduling {
        SystemBackgroundTaskScheduler()
    }

    private static func makeBackgroundScheduler(
        activeScheduleRepository: ActiveScheduleRepository,
        localScheduleRepository: LocalScheduleRepository,
        ruleRepository: RuleRepository,
        executor: any OperationExecutor,
        notifications: any SchedulingNotifications,
        publicSchedulesLoader: @escaping @Sendable () async throws -> [Schedule],
        taskScheduler: any BackgroundTaskScheduling
    ) -> BackgroundScheduler {
        BackgroundScheduler(
            activeScheduleRepository: activeScheduleRepository,
            localScheduleRepository: localScheduleRepository,
            ruleRepository: ruleRepository,
            executor: executor,
            notifications: notifications,
            publicSchedulesLoader: publicSchedulesLoader,
            taskScheduler: taskScheduler
        )
    }
}

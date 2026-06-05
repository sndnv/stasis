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

    init(
        appInfo: StasisApplicationInformation = StasisApplicationInformation(),
        settings: UserDefaults = .standard,
        configRepository: ConfigRepository? = nil,
        credentialsKeychain: Keychain = Keychain(service: KeychainCredentialsStore.defaultService),
        modelContainer: ModelContainer? = nil,
        backupStateStore: StateStore<[OperationId: BackupState]>? = nil,
        recoveryStateStore: StateStore<[OperationId: RecoveryState]>? = nil,
        analyticsClient: @escaping @Sendable () -> any AnalyticsClient = { NoOpAnalyticsClient() }
    ) {
        self.appInfo = appInfo
        self.settings = settings
        guard let configRepository = configRepository ?? ConfigRepository() else {
            preconditionFailure("failed to open ConfigRepository for App Group \(ConfigRepository.suiteName)")
        }
        self.configRepository = configRepository
        self.credentialsKeychain = credentialsKeychain

        let container: ModelContainer
        do {
            container = try modelContainer ?? PersistenceSchema.defaultContainer()
        } catch {
            preconditionFailure("failed to create SwiftData container: \(error)")
        }
        self.modelContainer = container
        self.ruleRepository = RuleRepository(modelContainer: container)
        self.activeScheduleRepository = ActiveScheduleRepository(modelContainer: container)
        self.localScheduleRepository = LocalScheduleRepository(modelContainer: container)

        let backupStore: StateStore<[OperationId: BackupState]>
        let recoveryStore: StateStore<[OperationId: RecoveryState]>
        do {
            backupStore = try backupStateStore ?? StateStores.backups()
            recoveryStore = try recoveryStateStore ?? StateStores.recoveries()
        } catch {
            preconditionFailure("failed to create state stores: \(error)")
        }

        self.trackers = DefaultTrackers(
            backup: DefaultBackupTracker(store: backupStore),
            recovery: DefaultRecoveryTracker(store: recoveryStore),
            server: DefaultServerTracker()
        )

        let persistence = DefaultAnalyticsPersistence(
            preferences: configRepository.preferencesStore,
            client: analyticsClient
        )
        self.analyticsPersistence = persistence
        self.analyticsCollector = DefaultAnalyticsCollector(
            app: appInfo,
            persistenceInterval: settings.analyticsPersistenceInterval(),
            transmissionInterval: settings.analyticsTransmissionInterval(),
            persistence: persistence
        )
    }
}

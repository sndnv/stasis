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

    let appStateModel: AppStateModel
    let sessionTokenStore: SessionTokenStore

    private(set) var session: AuthenticatedSession?
    private var tokenPersistenceTask: Task<Void, Never>?

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
        let resolvedConfigRepository = configRepository ?? Self.makeConfigRepository()
        self.configRepository = resolvedConfigRepository
        self.credentialsKeychain = credentialsKeychain
        let tokenStore = SessionTokenStore(keychain: credentialsKeychain)
        self.sessionTokenStore = tokenStore
        self.appStateModel = Self.makeAppStateModel(
            configRepository: resolvedConfigRepository,
            sessionTokenStore: tokenStore
        )

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

    func bootstrap(request: BootstrapRequest) async throws {
        await tearDownExistingSession()
        let outcome = try await Bootstrap.execute(
            request: request,
            configRepository: configRepository,
            ruleRepository: ruleRepository,
            credentialsKeychain: credentialsKeychain
        )
        sessionTokenStore.storePlaintextDeviceSecret(outcome.plaintextDeviceSecret)
        try await activateSession(provider: outcome.provider)
    }

    func login(username: String, password: String, rememberUsername: Bool) async throws {
        await tearDownExistingSession()
        let provider = try await Login.execute(
            username: username,
            password: password,
            configRepository: configRepository,
            credentialsKeychain: credentialsKeychain
        )
        configRepository.preferencesStore.saveUsername(rememberUsername ? username : nil)
        if let secret = try? await provider.currentDeviceSecret().get().secret {
            sessionTokenStore.storePlaintextDeviceSecret(secret)
        }
        try await activateSession(provider: provider)
    }

    func reEncryptDeviceSecret(currentPassword: String, oldPassword: String) async throws {
        if let session {
            let result = await session.credentialsProvider.reEncryptDeviceSecret(
                currentPassword: currentPassword, oldPassword: oldPassword
            )
            try result.get()
            try await session.refreshDeviceSecret()
            return
        }
        let preferences = configRepository.preferencesStore
        guard let apiConfig = try preferences.serverApiConfig() else {
            throw AppContainerError.notConfigured
        }
        let store = try KeychainCredentialsStore(
            apiConfig: apiConfig,
            preferences: preferences,
            keychain: credentialsKeychain
        )
        let result = await store.reEncryptDeviceSecret(
            currentUserPassword: currentPassword,
            oldUserPassword: oldPassword
        )
        try result.get()
    }

    func reinitializeDevice() async throws {
        try await resetConfiguration()
    }

    func updateUserPassword(currentPassword: String, newPassword: String) async throws {
        try await updateUserCredentials(
            currentPassword: currentPassword,
            newPassword: newPassword,
            newSalt: nil
        )
    }

    func updateUserSalt(currentPassword: String, newSalt: String) async throws {
        try await updateUserCredentials(
            currentPassword: currentPassword,
            newPassword: currentPassword,
            newSalt: newSalt
        )
    }

    private func updateUserCredentials(
        currentPassword: String,
        newPassword: String,
        newSalt: String?
    ) async throws {
        guard let session else { throw AppContainerError.notConfigured }
        guard await session.credentialsProvider.verifyUserPassword(currentPassword) else {
            throw InvalidUserCredentials()
        }
        let result = await session.credentialsProvider.updateUserCredentials(
            api: session.serverApiClient,
            currentPassword: currentPassword,
            newPassword: newPassword,
            newSalt: newSalt
        )
        _ = try result.get()
    }

    func importDeviceSecret(plaintext: Data, password: String) async throws {
        guard let session else { throw AppContainerError.notConfigured }
        guard await session.credentialsProvider.verifyUserPassword(password) else {
            throw InvalidUserCredentials()
        }
        let result = await session.credentialsProvider.updateDeviceSecret(
            plaintextDeviceSecret: plaintext, password: password
        )
        _ = try result.get()
        try await session.refreshDeviceSecret()
        sessionTokenStore.storePlaintextDeviceSecret(plaintext)
    }

    func pushDeviceSecret(password: String, remotePassword: String?) async throws {
        guard let session else { throw AppContainerError.notConfigured }
        guard await session.credentialsProvider.verifyUserPassword(password) else {
            throw InvalidUserCredentials()
        }
        let result = await session.credentialsProvider.pushDeviceSecret(
            api: session.serverApiClient, password: password, remotePassword: remotePassword
        )
        try result.get()
    }

    func pullDeviceSecret(password: String, remotePassword: String?) async throws {
        guard let session else { throw AppContainerError.notConfigured }
        guard await session.credentialsProvider.verifyUserPassword(password) else {
            throw InvalidUserCredentials()
        }
        let result = await session.credentialsProvider.pullDeviceSecret(
            api: session.serverApiClient, password: password, remotePassword: remotePassword
        )
        try result.get()
        try await session.refreshDeviceSecret()
        if let value = try? await session.credentialsProvider.currentDeviceSecret().get() {
            sessionTokenStore.storePlaintextDeviceSecret(value.secret)
        }
    }

    func remoteDeviceSecretExists() async throws -> Bool {
        guard let session else { throw AppContainerError.notConfigured }
        let result = await session.credentialsProvider.remoteDeviceSecretExists(
            api: session.serverApiClient
        )
        return try result.get()
    }

    func restoreSession() async {
        guard appStateModel.state == .restoring else { return }
        guard let provider = await buildProviderForRestore() else {
            sessionTokenStore.clear()
            appStateModel.transition(to: .configured)
            return
        }
        do {
            try await finishActivation(provider: provider)
        } catch {
            sessionTokenStore.clear()
            await provider.logout()
            appStateModel.transition(to: .configured)
        }
    }

    func logout() async {
        await tearDownExistingSession()
        sessionTokenStore.clear()
        appStateModel.transition(to: .configured)
    }

    func resetConfiguration() async throws {
        await tearDownExistingSession()
        sessionTokenStore.clear()
        configRepository.reset()
        try await ruleRepository.clear()
        try await activeScheduleRepository.clear()
        try await localScheduleRepository.clear()
        appStateModel.transition(to: .unconfigured)
    }

    private func tearDownExistingSession() async {
        tokenPersistenceTask?.cancel()
        tokenPersistenceTask = nil
        if let session {
            await session.credentialsProvider.logout()
        }
        session = nil
    }

    private func activateSession(provider: CredentialsProvider) async throws {
        if let core = try? await provider.core().get() {
            sessionTokenStore.storeCoreToken(core)
        }
        if let api = try? await provider.api().get() {
            sessionTokenStore.storeApiToken(api)
        }
        try await finishActivation(provider: provider)
    }

    private func finishActivation(provider: CredentialsProvider) async throws {
        session = try await AuthenticatedSession.make(
            credentialsProvider: provider,
            configRepository: configRepository,
            trackers: trackers,
            analytics: analyticsCollector
        )
        startTokenPersistence(provider: provider)
        appStateModel.transition(to: .authenticated)
    }

    private func startTokenPersistence(provider: CredentialsProvider) {
        let tokenStore = sessionTokenStore
        let coreUpdates = provider.coreTokenUpdates()
        let apiUpdates = provider.apiTokenUpdates()
        tokenPersistenceTask = Task.detached {
            await withTaskGroup(of: Void.self) { group in
                group.addTask {
                    for await update in coreUpdates {
                        switch update {
                        case .success(let token): tokenStore.storeCoreToken(token)
                        case .failure: tokenStore.clear()
                        }
                    }
                }
                group.addTask {
                    for await update in apiUpdates {
                        switch update {
                        case .success(let token): tokenStore.storeApiToken(token)
                        case .failure: tokenStore.clear()
                        }
                    }
                }
            }
        }
    }

    private func buildProviderForRestore() async -> CredentialsProvider? {
        guard let coreToken = sessionTokenStore.loadCoreToken(), coreToken.hasNotExpired,
              let apiToken = sessionTokenStore.loadApiToken(), apiToken.hasNotExpired,
              let plaintextSecret = sessionTokenStore.loadPlaintextDeviceSecret(),
              let digestedPassword = try? credentialsKeychain.string(account: KeychainCredentialsStore.digestedPasswordAccount)
        else { return nil }
        let preferences = configRepository.preferencesStore
        guard let authConfig = (try? preferences.authenticationConfig()) ?? nil,
              let apiConfig = (try? preferences.serverApiConfig()) ?? nil
        else { return nil }
        let oAuthClient: any OAuthClient
        let store: KeychainCredentialsStore
        do {
            oAuthClient = try DefaultOAuthClient(
                tokenEndpoint: authConfig.tokenEndpoint,
                client: authConfig.clientId,
                clientSecret: authConfig.clientSecret
            )
            store = try KeychainCredentialsStore(
                apiConfig: apiConfig,
                preferences: preferences,
                keychain: credentialsKeychain
            )
        } catch {
            return nil
        }
        let provider = CredentialsProvider(
            config: CredentialsProvider.Config(
                coreScope: authConfig.scopeCore,
                apiScope: authConfig.scopeApi,
                expirationTolerance: Bootstrap.defaultExpirationTolerance
            ),
            oAuthClient: oAuthClient,
            store: store
        )
        await provider.initialize(
            coreToken: coreToken,
            apiToken: apiToken,
            plaintextDeviceSecret: plaintextSecret,
            digestedUserPassword: digestedPassword
        )
        return provider
    }

    enum AppContainerError: Error, Equatable {
        case notConfigured
    }

    private static func makeAppStateModel(
        configRepository: ConfigRepository,
        sessionTokenStore: SessionTokenStore
    ) -> AppStateModel {
        AppStateModel(
            configRepository: configRepository,
            restorableSession: Self.canRestoreSession(sessionTokenStore: sessionTokenStore)
        )
    }

    private static func canRestoreSession(sessionTokenStore: SessionTokenStore) -> Bool {
        guard let core = sessionTokenStore.loadCoreToken(), core.hasNotExpired,
              let api = sessionTokenStore.loadApiToken(), api.hasNotExpired,
              sessionTokenStore.loadPlaintextDeviceSecret() != nil
        else { return false }
        return true
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

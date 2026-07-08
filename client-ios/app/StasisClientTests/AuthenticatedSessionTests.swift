import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@MainActor
@Suite("AuthenticatedSession")
struct AuthenticatedSessionTests {
    @Test("make produces a session populated with API/Core clients and an executor")
    func makeReturnsPopulatedSession() async throws {
        let environment = try TestEnvironment()
        let params = TestDefaults.bootstrapParams()
        let outcome = try await Bootstrap.execute(
            request: BootstrapRequest(
                serverBootstrapUrl: "https://server.test",
                bootstrapCode: "code",
                username: "user",
                userPassword: "pw",
                overwriteExisting: true,
                pullSecret: false,
                remotePassword: nil
            ),
            configRepository: environment.configRepository,
            ruleRepository: environment.ruleRepository,
            credentialsKeychain: environment.keychain,
            bootstrapClientFactory: environment.bootstrapClientFactory(.success(params)),
            oAuthClientFactory: environment.oAuthClientFactory(
                core: .success(.test()), api: .success(.test())
            ),
            apiClientFactory: environment.unusedApiClientFactory()
        )

        let session = try await AuthenticatedSession.make(
            credentialsProvider: outcome.provider,
            configRepository: environment.configRepository,
            trackers: try TestDefaults.trackers(),
            analytics: NoOpAnalyticsCollector(),
            notifications: MockSchedulingNotifications()
        )

        #expect(session.serverApiClient.server == params.serverApi.url)
        #expect(session.serverCoreClient.server == params.serverCore.address)
        #expect(session.serverApiClient.selfDevice.uuidString.lowercased() == params.serverApi.device.lowercased())
        #expect(session.serverCoreClient.selfNode.uuidString.lowercased() == params.serverCore.nodeId.lowercased())
    }

    @Test("make throws when the server API config is missing")
    func makeWithoutServerApiConfigThrows() async throws {
        let environment = try TestEnvironment()
        let provider = try makeUninitializedProvider()
        await #expect(throws: SessionError.missingServerApiConfig) {
            _ = try await AuthenticatedSession.make(
                credentialsProvider: provider,
                configRepository: environment.configRepository,
                trackers: try TestDefaults.trackers(),
                analytics: NoOpAnalyticsCollector(),
                notifications: MockSchedulingNotifications()
            )
        }
    }

    @Test("make throws when the server core config is missing")
    func makeWithoutServerCoreConfigThrows() async throws {
        let environment = try TestEnvironment()
        let preferences = environment.preferences
        preferences.set("https://api.test", forKey: ConfigRepository.Keys.ServerApi.url)
        preferences.set("user", forKey: ConfigRepository.Keys.ServerApi.user)
        preferences.set("salt", forKey: ConfigRepository.Keys.ServerApi.userSalt)
        preferences.set(UUID().uuidString, forKey: ConfigRepository.Keys.ServerApi.device)

        let provider = try makeUninitializedProvider()
        await #expect(throws: SessionError.missingServerCoreConfig) {
            _ = try await AuthenticatedSession.make(
                credentialsProvider: provider,
                configRepository: environment.configRepository,
                trackers: try TestDefaults.trackers(),
                analytics: NoOpAnalyticsCollector(),
                notifications: MockSchedulingNotifications()
            )
        }
    }

    @Test("make throws when the server API device id is not a valid UUID")
    func makeWithInvalidServerApiDeviceIdThrows() async throws {
        let environment = try TestEnvironment()
        let preferences = environment.preferences
        preferences.set("https://api.test", forKey: ConfigRepository.Keys.ServerApi.url)
        preferences.set("user", forKey: ConfigRepository.Keys.ServerApi.user)
        preferences.set("salt", forKey: ConfigRepository.Keys.ServerApi.userSalt)
        preferences.set("not-a-uuid", forKey: ConfigRepository.Keys.ServerApi.device)
        preferences.set("https://core.test", forKey: ConfigRepository.Keys.ServerCore.address)
        preferences.set(UUID().uuidString, forKey: ConfigRepository.Keys.ServerCore.nodeId)

        let provider = try makeUninitializedProvider()
        await #expect(throws: SessionError.invalidServerApiDeviceId("not-a-uuid")) {
            _ = try await AuthenticatedSession.make(
                credentialsProvider: provider,
                configRepository: environment.configRepository,
                trackers: try TestDefaults.trackers(),
                analytics: NoOpAnalyticsCollector(),
                notifications: MockSchedulingNotifications()
            )
        }
    }

    @Test("make throws when the server core node id is not a valid UUID")
    func makeWithInvalidServerCoreNodeIdThrows() async throws {
        let environment = try TestEnvironment()
        let preferences = environment.preferences
        preferences.set("https://api.test", forKey: ConfigRepository.Keys.ServerApi.url)
        preferences.set("user", forKey: ConfigRepository.Keys.ServerApi.user)
        preferences.set("salt", forKey: ConfigRepository.Keys.ServerApi.userSalt)
        preferences.set(UUID().uuidString, forKey: ConfigRepository.Keys.ServerApi.device)
        preferences.set("https://core.test", forKey: ConfigRepository.Keys.ServerCore.address)
        preferences.set("not-a-uuid", forKey: ConfigRepository.Keys.ServerCore.nodeId)

        let provider = try makeUninitializedProvider()
        await #expect(throws: SessionError.invalidServerCoreNodeId("not-a-uuid")) {
            _ = try await AuthenticatedSession.make(
                credentialsProvider: provider,
                configRepository: environment.configRepository,
                trackers: try TestDefaults.trackers(),
                analytics: NoOpAnalyticsCollector(),
                notifications: MockSchedulingNotifications()
            )
        }
    }

    private func makeUninitializedProvider() throws -> CredentialsProvider {
        let store = try KeychainCredentialsStore(
            apiConfig: TestDefaults.apiConfig(),
            preferences: TestDefaults.isolatedDefaults(),
            keychain: Keychain(service: "stasis.tests.\(UUID().uuidString)")
        )
        return CredentialsProvider(
            config: .init(coreScope: "core", apiScope: "api", expirationTolerance: 60),
            oAuthClient: MockOAuthClient(coreOutcome: .success(.test()), apiOutcome: .success(.test())),
            store: store
        )
    }
}

@Suite("SessionError")
struct SessionErrorTests {
    @Test("describes each case")
    func messages() {
        #expect(SessionError.missingServerApiConfig.errorDescription == "Server API configuration is missing")
        #expect(SessionError.missingServerCoreConfig.errorDescription == "Server core configuration is missing")
        #expect(SessionError.invalidServerApiDeviceId("test").errorDescription == "Invalid server API device ID [test]")
        #expect(SessionError.invalidServerCoreNodeId("test").errorDescription == "Invalid server core node ID [test]")
    }
}

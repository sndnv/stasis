import Foundation
@testable import StasisClient
import StasisClientLib

@MainActor
struct TestEnvironment {
    let preferences: UserDefaults
    let configRepository: ConfigRepository
    let ruleRepository: RuleRepository
    let keychain: Keychain

    init() throws {
        self.preferences = TestDefaults.isolatedDefaults()
        self.configRepository = ConfigRepository(preferences: preferences)
        self.ruleRepository = RuleRepository(
            modelContainer: try PersistenceSchema.inMemoryContainer()
        )
        self.keychain = Keychain(service: "stasis.tests.\(UUID().uuidString)")
    }

    func bootstrapClientFactory(
        _ outcome: Result<DeviceBootstrapParameters, any Error>
    ) -> Bootstrap.BootstrapClientFactory {
        { _ in MockServerBootstrapEndpointClient(outcome: outcome) }
    }

    func oAuthClientFactory(
        core: Result<AccessTokenResponse, any Error>,
        api: Result<AccessTokenResponse, any Error>
    ) -> @Sendable (String, String, String) throws -> any OAuthClient {
        { _, _, _ in MockOAuthClient(coreOutcome: core, apiOutcome: api) }
    }

    func unusedApiClientFactory() -> Bootstrap.ApiClientFactory {
        { _, _ in fatalError("apiClientFactory should not be called in this test") }
    }
}

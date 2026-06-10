import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@MainActor
@Suite("Login")
struct LoginTests {
    @Test("missing authentication config throws")
    func missingAuthenticationConfig() async throws {
        let environment = try TestEnvironment()
        await #expect(throws: LoginError.missingAuthenticationConfig) {
            _ = try await Login.execute(
                username: "user",
                password: "pw",
                configRepository: environment.configRepository,
                credentialsKeychain: environment.keychain
            )
        }
    }

    @Test("missing server API config throws")
    func missingServerApiConfig() async throws {
        let environment = try TestEnvironment()
        let preferences = environment.preferences
        preferences.set("http://localhost/token", forKey: ConfigRepository.Keys.Authentication.tokenEndpoint)
        preferences.set("client-id", forKey: ConfigRepository.Keys.Authentication.clientId)
        preferences.set("client-secret", forKey: ConfigRepository.Keys.Authentication.clientSecret)
        preferences.set("scope-api", forKey: ConfigRepository.Keys.Authentication.scopeApi)
        preferences.set("scope-core", forKey: ConfigRepository.Keys.Authentication.scopeCore)

        await #expect(throws: LoginError.missingServerApiConfig) {
            _ = try await Login.execute(
                username: "user",
                password: "pw",
                configRepository: environment.configRepository,
                credentialsKeychain: environment.keychain,
                oAuthClientFactory: environment.oAuthClientFactory(
                    core: .success(.test()), api: .success(.test())
                )
            )
        }
    }

    @Test("OAuth failure after bootstrap is propagated")
    func oauthFailureAfterBootstrap() async throws {
        let environment = try TestEnvironment()
        _ = try await Bootstrap.execute(
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
            bootstrapClientFactory: environment.bootstrapClientFactory(.success(TestDefaults.bootstrapParams())),
            oAuthClientFactory: environment.oAuthClientFactory(
                core: .success(.test()), api: .success(.test())
            ),
            apiClientFactory: environment.unusedApiClientFactory()
        )

        await #expect(throws: AccessDeniedFailure.self) {
            _ = try await Login.execute(
                username: "user",
                password: "pw",
                configRepository: environment.configRepository,
                credentialsKeychain: environment.keychain,
                oAuthClientFactory: environment.oAuthClientFactory(
                    core: .success(.test()),
                    api: .failure(AccessDeniedFailure())
                )
            )
        }
    }

    @Test("happy path after bootstrap returns a usable provider")
    func happyPathAfterBootstrap() async throws {
        let environment = try TestEnvironment()
        let userPassword = "pw"
        _ = try await Bootstrap.execute(
            request: BootstrapRequest(
                serverBootstrapUrl: "https://server.test",
                bootstrapCode: "code",
                username: "user",
                userPassword: userPassword,
                overwriteExisting: true,
                pullSecret: false,
                remotePassword: nil
            ),
            configRepository: environment.configRepository,
            ruleRepository: environment.ruleRepository,
            credentialsKeychain: environment.keychain,
            bootstrapClientFactory: environment.bootstrapClientFactory(
                .success(TestDefaults.bootstrapParams())
            ),
            oAuthClientFactory: environment.oAuthClientFactory(
                core: .success(.test()), api: .success(.test())
            ),
            apiClientFactory: environment.unusedApiClientFactory()
        )

        let provider = try await Login.execute(
            username: "user",
            password: userPassword,
            configRepository: environment.configRepository,
            credentialsKeychain: environment.keychain,
            oAuthClientFactory: environment.oAuthClientFactory(
                core: .success(.test()), api: .success(.test())
            )
        )

        let secret = await provider.currentDeviceSecret()
        #expect((try? secret.get()) != nil)
    }
}

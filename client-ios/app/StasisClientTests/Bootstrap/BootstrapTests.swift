import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@MainActor
@Suite("Bootstrap")
struct BootstrapTests {
    @Test("happy path saves config, clears username, persists secret, returns outcome")
    func happyPath() async throws {
        let context = try TestEnvironment()
        let outcome = try await Bootstrap.execute(
            request: defaultRequest(),
            configRepository: context.configRepository,
            ruleRepository: context.ruleRepository,
            credentialsKeychain: context.keychain,
            bootstrapClientFactory: context.bootstrapClientFactory(.success(TestDefaults.bootstrapParams())),
            oAuthClientFactory: context.oAuthClientFactory(
                core: .success(.test()), api: .success(.test())
            ),
            apiClientFactory: context.unusedApiClientFactory()
        )

        #expect(try context.configRepository.available())
        #expect(context.preferences.savedUsername() == nil)
        #expect((try? context.preferences.encryptedDeviceSecret()) != nil)
        #expect(!outcome.plaintextDeviceSecret.isEmpty)
        let secret = await outcome.provider.currentDeviceSecret()
        #expect((try? secret.get()) != nil)
    }

    @Test("failure after persistence rolls back config and rules")
    func rollsBackOnFailure() async throws {
        let context = try TestEnvironment()
        await #expect(throws: AccessDeniedFailure.self) {
            _ = try await Bootstrap.execute(
                request: defaultRequest(),
                configRepository: context.configRepository,
                ruleRepository: context.ruleRepository,
                credentialsKeychain: context.keychain,
                bootstrapClientFactory: context.bootstrapClientFactory(.success(TestDefaults.bootstrapParams())),
                oAuthClientFactory: context.oAuthClientFactory(
                    core: .failure(AccessDeniedFailure()),
                    api: .success(.test())
                ),
                apiClientFactory: context.unusedApiClientFactory()
            )
        }
        #expect(try context.configRepository.available() == false)
        let rules = try await context.ruleRepository.rules()
        #expect(rules.isEmpty)
    }

    @Test("invalid user UUID throws BootstrapError.invalidUserId")
    func invalidUserId() async throws {
        let context = try TestEnvironment()
        let params = TestDefaults.bootstrapParamsWithApi(user: "not-a-uuid")
        await #expect(throws: BootstrapError.invalidUserId("not-a-uuid")) {
            _ = try await Bootstrap.execute(
                request: defaultRequest(),
                configRepository: context.configRepository,
                ruleRepository: context.ruleRepository,
                credentialsKeychain: context.keychain,
                bootstrapClientFactory: context.bootstrapClientFactory(.success(params)),
                oAuthClientFactory: context.oAuthClientFactory(
                    core: .success(.test()), api: .success(.test())
                ),
                apiClientFactory: context.unusedApiClientFactory()
            )
        }
    }

    @Test("invalid device UUID throws BootstrapError.invalidDeviceId")
    func invalidDeviceId() async throws {
        let context = try TestEnvironment()
        let params = TestDefaults.bootstrapParamsWithApi(device: "not-a-uuid")
        await #expect(throws: BootstrapError.invalidDeviceId("not-a-uuid")) {
            _ = try await Bootstrap.execute(
                request: defaultRequest(),
                configRepository: context.configRepository,
                ruleRepository: context.ruleRepository,
                credentialsKeychain: context.keychain,
                bootstrapClientFactory: context.bootstrapClientFactory(.success(params)),
                oAuthClientFactory: context.oAuthClientFactory(
                    core: .success(.test()), api: .success(.test())
                ),
                apiClientFactory: context.unusedApiClientFactory()
            )
        }
    }

    @Test("bootstrap endpoint failure is propagated")
    func bootstrapEndpointFailure() async throws {
        let context = try TestEnvironment()
        await #expect(throws: InvalidBootstrapCodeFailure.self) {
            _ = try await Bootstrap.execute(
                request: defaultRequest(),
                configRepository: context.configRepository,
                ruleRepository: context.ruleRepository,
                credentialsKeychain: context.keychain,
                bootstrapClientFactory: context.bootstrapClientFactory(.failure(InvalidBootstrapCodeFailure())),
                oAuthClientFactory: context.oAuthClientFactory(
                    core: .success(.test()), api: .success(.test())
                ),
                apiClientFactory: context.unusedApiClientFactory()
            )
        }
    }

    @Test("OAuth core token failure is propagated")
    func oauthFailure() async throws {
        let context = try TestEnvironment()
        await #expect(throws: AccessDeniedFailure.self) {
            _ = try await Bootstrap.execute(
                request: defaultRequest(),
                configRepository: context.configRepository,
                ruleRepository: context.ruleRepository,
                credentialsKeychain: context.keychain,
                bootstrapClientFactory: context.bootstrapClientFactory(.success(TestDefaults.bootstrapParams())),
                oAuthClientFactory: context.oAuthClientFactory(
                    core: .failure(AccessDeniedFailure()),
                    api: .success(.test())
                ),
                apiClientFactory: context.unusedApiClientFactory()
            )
        }
    }
}

private func defaultRequest(pullSecret: Bool = false) -> BootstrapRequest {
    BootstrapRequest(
        serverBootstrapUrl: "https://server.test",
        bootstrapCode: "code-42",
        username: "user",
        userPassword: "pw",
        overwriteExisting: true,
        pullSecret: pullSecret,
        remotePassword: nil
    )
}

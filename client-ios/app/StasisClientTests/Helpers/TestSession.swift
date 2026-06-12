import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport

@MainActor
enum TestSession {
    static func make(
        api: StasisClientLibTestSupport.MockServerApiEndpointClient = StasisClientLibTestSupport.MockServerApiEndpointClient(),
        core: any ServerCoreEndpointClient = StubServerCoreEndpointClient(),
        executor: any OperationExecutor = MockOperationExecutor(),
        caches: AuthenticatedSession.CachesBundle? = nil
    ) throws -> AuthenticatedSession {
        AuthenticatedSession(
            credentialsProvider: try makeProvider(),
            serverApiClient: api,
            serverCoreClient: core,
            operationExecutor: executor,
            secretRef: AuthenticatedSession.SecretRef(),
            caches: caches
        )
    }

    private static func makeProvider() throws -> CredentialsProvider {
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

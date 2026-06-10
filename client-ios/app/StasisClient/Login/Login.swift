import Foundation
import StasisClientLib

enum Login {
    typealias OAuthClientFactory = @Sendable (String, String, String) throws -> any OAuthClient

    static let defaultExpirationTolerance: TimeInterval = 120

    static let defaultOAuthClientFactory: OAuthClientFactory = { endpoint, client, secret in
        try DefaultOAuthClient(tokenEndpoint: endpoint, client: client, clientSecret: secret)
    }

    static func execute(
        username: String,
        password: String,
        configRepository: ConfigRepository,
        credentialsKeychain: Keychain,
        expirationTolerance: TimeInterval = Login.defaultExpirationTolerance,
        oAuthClientFactory: OAuthClientFactory = Login.defaultOAuthClientFactory
    ) async throws -> CredentialsProvider {
        let preferences = configRepository.preferencesStore
        guard let authConfig = try preferences.authenticationConfig() else {
            throw LoginError.missingAuthenticationConfig
        }
        guard let apiConfig = try preferences.serverApiConfig() else {
            throw LoginError.missingServerApiConfig
        }
        let oAuthClient = try oAuthClientFactory(
            authConfig.tokenEndpoint,
            authConfig.clientId,
            authConfig.clientSecret
        )
        let store = try KeychainCredentialsStore(
            apiConfig: apiConfig,
            preferences: preferences,
            keychain: credentialsKeychain
        )
        let provider = CredentialsProvider(
            config: CredentialsProvider.Config(
                coreScope: authConfig.scopeCore,
                apiScope: authConfig.scopeApi,
                expirationTolerance: expirationTolerance
            ),
            oAuthClient: oAuthClient,
            store: store
        )
        let loginResult = await provider.login(username: username, password: password)
        if case .failure(let error) = loginResult {
            throw error
        }
        return provider
    }
}

enum LoginError: Error, Equatable {
    case missingAuthenticationConfig
    case missingServerApiConfig
}

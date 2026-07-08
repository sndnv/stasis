import Foundation
import StasisClientLib

enum Bootstrap {
    typealias BootstrapClientFactory = @Sendable (String) -> any ServerBootstrapEndpointClient
    typealias OAuthClientFactory = @Sendable (String, String, String) throws -> any OAuthClient
    typealias ApiClientFactory = @Sendable (Config.ServerApi, any HttpCredentialsProvider) -> any ServerApiEndpointClient

    static let defaultExpirationTolerance: TimeInterval = 120

    static let defaultBootstrapClientFactory: BootstrapClientFactory = { url in
        #if DEBUG
        if MockConfig.isMockServer(url) {
            return MockServerBootstrapEndpointClient()
        }
        #endif
        return DefaultServerBootstrapEndpointClient(serverBootstrapUrl: url)
    }

    static let defaultOAuthClientFactory: OAuthClientFactory = { endpoint, client, secret in
        #if DEBUG
        if MockConfig.isMockTokenEndpoint(endpoint) {
            return MockOAuthClient()
        }
        #endif
        return try DefaultOAuthClient(tokenEndpoint: endpoint, client: client, clientSecret: secret)
    }

    static let defaultApiClientFactory: ApiClientFactory = { apiConfig, credentials in
        #if DEBUG
        if MockConfig.isMockServer(apiConfig.url) {
            return MockServerApiEndpointClient()
        }
        #endif
        let device = UUID(uuidString: apiConfig.device) ?? UUID()
        return DefaultServerApiEndpointClient(
            serverApiUrl: apiConfig.url,
            credentialsProvider: credentials,
            decryption: .disabled,
            selfDevice: device
        )
    }

    struct Outcome: Sendable {
        let provider: CredentialsProvider
        let plaintextDeviceSecret: Data
    }

    static func execute(
        request: BootstrapRequest,
        configRepository: ConfigRepository,
        ruleRepository: RuleRepository,
        credentialsKeychain: Keychain,
        expirationTolerance: TimeInterval = Bootstrap.defaultExpirationTolerance,
        bootstrapClientFactory: BootstrapClientFactory = Bootstrap.defaultBootstrapClientFactory,
        oAuthClientFactory: OAuthClientFactory = Bootstrap.defaultOAuthClientFactory,
        apiClientFactory: ApiClientFactory = Bootstrap.defaultApiClientFactory
    ) async throws -> Outcome {
        let bootstrapClient = bootstrapClientFactory(request.serverBootstrapUrl)
        let params = try await bootstrapClient.execute(bootstrapCode: request.bootstrapCode)

        configRepository.bootstrap(params: params)
        let preferences = configRepository.preferencesStore
        preferences.saveUsername(nil)

        do {
            let identities = try resolveIdentities(params: params)
            let apiConfig = toApiConfig(params.serverApi)
            let oAuthClient = try oAuthClientFactory(
                params.authentication.tokenEndpoint,
                params.authentication.clientId,
                params.authentication.clientSecret
            )
            let tokens = try await mintTokens(
                request: request, params: params, identities: identities,
                preferences: preferences, oAuthClient: oAuthClient
            )
            let plaintextSecret = try await resolveDeviceSecret(
                request: request, apiConfig: apiConfig, identities: identities,
                apiToken: tokens.apiToken, preferences: preferences,
                apiClientFactory: apiClientFactory
            )
            let provider = try await initializeProvider(
                params: params, apiConfig: apiConfig, tokens: tokens,
                plaintextSecret: plaintextSecret, preferences: preferences,
                credentialsKeychain: credentialsKeychain, oAuthClient: oAuthClient,
                expirationTolerance: expirationTolerance
            )
            try await ruleRepository.bootstrap()
            return Outcome(provider: provider, plaintextDeviceSecret: plaintextSecret)
        } catch {
            configRepository.reset()
            try? await ruleRepository.clear()
            throw error
        }
    }

    private struct Identities: Sendable {
        let userId: UserId
        let deviceId: DeviceId
    }

    private struct MintedTokens: Sendable {
        let coreToken: AccessTokenResponse
        let apiToken: AccessTokenResponse
        let digestedUserPassword: String
    }

    private static func resolveIdentities(params: DeviceBootstrapParameters) throws -> Identities {
        guard let userId = UUID(uuidString: params.serverApi.user) else {
            throw BootstrapError.invalidUserId(params.serverApi.user)
        }
        guard let deviceId = UUID(uuidString: params.serverApi.device) else {
            throw BootstrapError.invalidDeviceId(params.serverApi.device)
        }
        return Identities(userId: userId, deviceId: deviceId)
    }

    private static func toApiConfig(_ source: DeviceBootstrapParameters.ServerApi) -> Config.ServerApi {
        Config.ServerApi(url: source.url, user: source.user, userSalt: source.userSalt, device: source.device)
    }

    private static func mintTokens(
        request: BootstrapRequest,
        params: DeviceBootstrapParameters,
        identities: Identities,
        preferences: UserDefaults,
        oAuthClient: any OAuthClient
    ) async throws -> MintedTokens {
        let secretsConfig = try preferences.secretsConfig()
        let userPassword = UserPassword(
            user: identities.userId,
            salt: params.serverApi.userSalt,
            password: request.userPassword,
            target: secretsConfig
        )
        let authenticationPassword = userPassword.toAuthenticationPassword()
        let extractedAuthPassword = try authenticationPassword.extract()
        let digestedUserPassword = authenticationPassword.digested()
        let coreToken = try await oAuthClient.token(
            scope: params.authentication.scopes.core, parameters: .clientCredentials
        ).get()
        let apiToken = try await oAuthClient.token(
            scope: params.authentication.scopes.api,
            parameters: .resourceOwnerPasswordCredentials(
                username: request.username, password: extractedAuthPassword
            )
        ).get()
        return MintedTokens(
            coreToken: coreToken, apiToken: apiToken,
            digestedUserPassword: digestedUserPassword
        )
    }

    private static func resolveDeviceSecret(
        request: BootstrapRequest,
        apiConfig: Config.ServerApi,
        identities: Identities,
        apiToken: AccessTokenResponse,
        preferences: UserDefaults,
        apiClientFactory: ApiClientFactory
    ) async throws -> Data {
        if request.overwriteExisting && request.pullSecret {
            return try await pullOrCreateDeviceSecret(
                request: request, apiConfig: apiConfig, identities: identities,
                apiToken: apiToken, preferences: preferences, apiClientFactory: apiClientFactory
            )
        } else if request.overwriteExisting {
            return try await createNewDeviceSecret(
                request: request, apiConfig: apiConfig,
                identities: identities, preferences: preferences
            )
        } else {
            let loadResult = await Secrets.loadDeviceSecret(
                user: identities.userId,
                userSalt: apiConfig.userSalt,
                userPassword: request.userPassword,
                device: identities.deviceId,
                preferences: preferences
            )
            return try loadResult.get().secret
        }
    }

    private static func pullOrCreateDeviceSecret(
        request: BootstrapRequest,
        apiConfig: Config.ServerApi,
        identities: Identities,
        apiToken: AccessTokenResponse,
        preferences: UserDefaults,
        apiClientFactory: ApiClientFactory
    ) async throws -> Data {
        let apiClient = apiClientFactory(
            apiConfig,
            StaticHttpCredentialsProvider(.oauth2BearerToken(token: apiToken.accessToken))
        )
        let pullResult = await Secrets.pullDeviceSecret(
            user: identities.userId,
            userSalt: apiConfig.userSalt,
            userPassword: request.userPassword,
            remotePassword: request.remotePassword,
            device: identities.deviceId,
            preferences: preferences,
            api: apiClient
        )
        switch pullResult {
        case .success(let secret):
            return secret.secret
        case .failure(let error):
            if error is ResourceMissingFailure {
                return try await createNewDeviceSecret(
                    request: request, apiConfig: apiConfig,
                    identities: identities, preferences: preferences
                )
            } else {
                throw error
            }
        }
    }

    private static func createNewDeviceSecret(
        request: BootstrapRequest,
        apiConfig: Config.ServerApi,
        identities: Identities,
        preferences: UserDefaults
    ) async throws -> Data {
        let createResult = await Secrets.createDeviceSecret(
            user: identities.userId,
            userSalt: apiConfig.userSalt,
            userPassword: request.userPassword,
            device: identities.deviceId,
            preferences: preferences
        )
        return try createResult.get().secret
    }

    private static func initializeProvider(
        params: DeviceBootstrapParameters,
        apiConfig: Config.ServerApi,
        tokens: MintedTokens,
        plaintextSecret: Data,
        preferences: UserDefaults,
        credentialsKeychain: Keychain,
        oAuthClient: any OAuthClient,
        expirationTolerance: TimeInterval
    ) async throws -> CredentialsProvider {
        let store = try KeychainCredentialsStore(
            apiConfig: apiConfig,
            preferences: preferences,
            keychain: credentialsKeychain
        )
        let provider = CredentialsProvider(
            config: CredentialsProvider.Config(
                coreScope: params.authentication.scopes.core,
                apiScope: params.authentication.scopes.api,
                expirationTolerance: expirationTolerance
            ),
            oAuthClient: oAuthClient,
            store: store
        )
        await provider.initialize(
            coreToken: tokens.coreToken,
            apiToken: tokens.apiToken,
            plaintextDeviceSecret: plaintextSecret,
            digestedUserPassword: tokens.digestedUserPassword
        )
        return provider
    }
}

enum BootstrapError: Error, Equatable, LocalizedError {
    case invalidUserId(String)
    case invalidDeviceId(String)

    var errorDescription: String? {
        switch self {
        case .invalidUserId(let value):
            "Invalid user ID [\(value)]"
        case .invalidDeviceId(let value):
            "Invalid device ID [\(value)]"
        }
    }
}

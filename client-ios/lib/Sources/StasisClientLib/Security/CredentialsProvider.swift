import Foundation

public actor CredentialsProvider {
    public struct Config: Sendable, Equatable {
        public let coreScope: String
        public let apiScope: String
        public let expirationTolerance: TimeInterval

        public init(coreScope: String, apiScope: String, expirationTolerance: TimeInterval) {
            self.coreScope = coreScope
            self.apiScope = apiScope
            self.expirationTolerance = expirationTolerance
        }
    }

    private let config: Config
    private let oAuthClient: any OAuthClient
    private let store: any CredentialsStore

    private let coreBroadcaster: TokenUpdateBroadcaster
    private let apiBroadcaster: TokenUpdateBroadcaster

    private let coreManager: OAuthTokenManager
    private let apiManager: OAuthTokenManager

    private var deviceSecret: Result<DeviceSecret, Error> = .failure(MissingDeviceSecret())

    public init(
        config: Config,
        oAuthClient: any OAuthClient,
        store: any CredentialsStore
    ) {
        self.config = config
        self.oAuthClient = oAuthClient
        self.store = store

        let coreBroadcaster = TokenUpdateBroadcaster()
        let apiBroadcaster = TokenUpdateBroadcaster()
        self.coreBroadcaster = coreBroadcaster
        self.apiBroadcaster = apiBroadcaster

        self.coreManager = OAuthTokenManager(
            oAuthClient: oAuthClient,
            onTokenUpdated: { result in coreBroadcaster.publish(result) },
            expirationTolerance: config.expirationTolerance
        )
        self.apiManager = OAuthTokenManager(
            oAuthClient: oAuthClient,
            onTokenUpdated: { result in apiBroadcaster.publish(result) },
            expirationTolerance: config.expirationTolerance
        )
    }

    public func core() async -> Result<AccessTokenResponse, Error> {
        await coreManager.token()
    }

    public func api() async -> Result<AccessTokenResponse, Error> {
        await apiManager.token()
    }

    public func currentDeviceSecret() -> Result<DeviceSecret, Error> {
        deviceSecret
    }

    public func initialize(
        coreToken: AccessTokenResponse,
        apiToken: AccessTokenResponse,
        plaintextDeviceSecret: Data,
        digestedUserPassword: String
    ) async {
        if let coreExpiresIn = Self.jwtExpiresIn(coreToken.accessToken, tolerance: config.expirationTolerance),
           let apiExpiresIn = Self.jwtExpiresIn(apiToken.accessToken, tolerance: config.expirationTolerance) {
            let coreUpdated = AccessTokenResponse(
                accessToken: coreToken.accessToken,
                refreshToken: coreToken.refreshToken,
                expiresIn: coreExpiresIn,
                scope: coreToken.scope
            )
            let apiUpdated = AccessTokenResponse(
                accessToken: apiToken.accessToken,
                refreshToken: apiToken.refreshToken,
                expiresIn: apiExpiresIn,
                scope: apiToken.scope
            )
            await coreManager.configureWithClientCredentials(.success(coreUpdated))
            await apiManager.configureWithRefreshToken(.success(apiUpdated))
            deviceSecret = .success(store.initDeviceSecret(plaintextDeviceSecret))
            store.initDigestedUserPassword(digestedUserPassword)
        } else if let refreshToken = apiToken.refreshToken {
            await initialize(
                apiRefreshToken: refreshToken,
                plaintextDeviceSecret: plaintextDeviceSecret,
                digestedUserPassword: digestedUserPassword
            )
        } else {
            await coreManager.configureWithClientCredentials(.failure(TokenExpired()))
            await apiManager.configureWithRefreshToken(.failure(TokenExpired()))
            deviceSecret = .failure(MissingDeviceSecret())
        }
    }

    public func initialize(
        apiRefreshToken: String,
        plaintextDeviceSecret: Data,
        digestedUserPassword: String
    ) async {
        let coreTokenResponse = await oAuthClient.token(
            scope: config.coreScope, parameters: .clientCredentials
        )
        let apiTokenResponse = await oAuthClient.token(
            scope: config.apiScope, parameters: .refreshToken(apiRefreshToken)
        )
        await coreManager.configureWithClientCredentials(coreTokenResponse)
        await apiManager.configureWithRefreshToken(apiTokenResponse)
        deviceSecret = .success(store.initDeviceSecret(plaintextDeviceSecret))
        store.initDigestedUserPassword(digestedUserPassword)
    }

    public func login(
        username: String,
        password: String
    ) async -> Result<(DeviceSecret, String), Error> {
        let secretResult = await store.loadDeviceSecret(userPassword: password)
        switch secretResult {
        case .failure(let error):
            deviceSecret = .failure(error)
            return .failure(error)

        case .success(let secret):
            let authPassword = store.getAuthenticationPassword(password)
            let extractedAuthPassword: String
            do {
                extractedAuthPassword = try authPassword.extract()
            } catch {
                return .failure(error)
            }

            let coreTokenResponse = await oAuthClient.token(
                scope: config.coreScope, parameters: .clientCredentials
            )
            let apiTokenResponse = await oAuthClient.token(
                scope: config.apiScope,
                parameters: .resourceOwnerPasswordCredentials(
                    username: username, password: extractedAuthPassword
                )
            )
            let digested = authPassword.digested()

            await coreManager.configureWithClientCredentials(coreTokenResponse)
            await apiManager.configureWithRefreshToken(apiTokenResponse)
            deviceSecret = .success(secret)
            store.initDigestedUserPassword(digested)

            if case .failure(let error) = coreTokenResponse { return .failure(error) }
            if case .failure(let error) = apiTokenResponse { return .failure(error) }
            return .success((secret, digested))
        }
    }

    public func logout() async {
        await coreManager.reset()
        await apiManager.reset()
        deviceSecret = .failure(MissingDeviceSecret())
        store.initDigestedUserPassword(nil)
    }

    public func verifyUserPassword(_ password: String) async -> Bool {
        await store.verifyUserPassword(password)
    }

    public func updateUserCredentials(
        api: any ServerApiEndpointClient,
        currentPassword: String,
        newPassword: String,
        newSalt: String?
    ) async -> Result<String, Error> {
        let updateResult = await store.updateUserCredentials(
            api: api,
            currentUserPassword: currentPassword,
            newUserPassword: newPassword,
            newUserSalt: newSalt
        )
        switch updateResult {
        case .failure(let error):
            return .failure(error)
        case .success(let updated):
            let extracted: String
            do {
                extracted = try updated.extract()
            } catch {
                return .failure(error)
            }
            do {
                try await api.resetUserPassword(request: ResetUserPassword(rawPassword: extracted))
                return .success(updated.digested())
            } catch {
                return .failure(error)
            }
        }
    }

    public func updateDeviceSecret(
        plaintextDeviceSecret: Data,
        password: String
    ) async -> Result<DeviceSecret, Error> {
        let result = await store.storeDeviceSecret(plaintextDeviceSecret, userPassword: password)
        deviceSecret = result
        return result
    }

    public func pushDeviceSecret(
        api: any ServerApiEndpointClient,
        password: String,
        remotePassword: String?
    ) async -> Result<Void, Error> {
        await store.pushDeviceSecret(api: api, userPassword: password, remotePassword: remotePassword)
    }

    public func pullDeviceSecret(
        api: any ServerApiEndpointClient,
        password: String,
        remotePassword: String?
    ) async -> Result<Void, Error> {
        let result = await store.pullDeviceSecret(
            api: api, userPassword: password, remotePassword: remotePassword
        )
        if case .success = result {
            deviceSecret = result
        }
        return result.map { _ in () }
    }

    public func reEncryptDeviceSecret(
        currentPassword: String,
        oldPassword: String
    ) async -> Result<Void, Error> {
        await store.reEncryptDeviceSecret(
            currentUserPassword: currentPassword, oldUserPassword: oldPassword
        )
    }

    public func remoteDeviceSecretExists(
        api: any ServerApiEndpointClient
    ) async -> Result<Bool, Error> {
        do {
            return .success(try await api.deviceKeyExists())
        } catch {
            return .failure(error)
        }
    }

    public nonisolated func coreCredentialsProvider() -> any HttpCredentialsProvider {
        ManagedHttpCredentialsProvider { [weak self] in
            await self?.core() ?? .failure(MissingDeviceSecret())
        }
    }

    public nonisolated func apiCredentialsProvider() -> any HttpCredentialsProvider {
        ManagedHttpCredentialsProvider { [weak self] in
            await self?.api() ?? .failure(MissingDeviceSecret())
        }
    }

    public nonisolated func coreTokenUpdates() -> AsyncStream<Result<AccessTokenResponse, Error>> {
        coreBroadcaster.subscribe()
    }

    public nonisolated func apiTokenUpdates() -> AsyncStream<Result<AccessTokenResponse, Error>> {
        apiBroadcaster.subscribe()
    }

    private static func jwtExpiresIn(_ jwt: String, tolerance: TimeInterval) -> Int64? {
        guard let payload = try? AccessTokenResponse.decodeJwtPayload(jwt),
              let exp = (payload["exp"] as? NSNumber)?.doubleValue
        else { return nil }
        let now = Date()
        if Date(timeIntervalSince1970: exp) < now.addingTimeInterval(-tolerance) {
            return nil
        }
        return Int64(abs(exp - now.timeIntervalSince1970))
    }
}

private struct ManagedHttpCredentialsProvider: HttpCredentialsProvider {
    let fetch: @Sendable () async -> Result<AccessTokenResponse, Error>

    func credentials() async -> HttpCredentials {
        switch await fetch() {
        case .success(let response): return .oauth2BearerToken(token: response.accessToken)
        case .failure: return .none
        }
    }
}

private final class TokenUpdateBroadcaster: @unchecked Sendable {
    private let lock = NSLock()
    private var subscribers: [UUID: AsyncStream<Result<AccessTokenResponse, Error>>.Continuation] = [:]

    func subscribe() -> AsyncStream<Result<AccessTokenResponse, Error>> {
        let (stream, continuation) = AsyncStream.makeStream(
            of: Result<AccessTokenResponse, Error>.self
        )
        let id = UUID()
        lock.lock()
        subscribers[id] = continuation
        lock.unlock()
        continuation.onTermination = { [weak self] _ in self?.remove(id) }
        return stream
    }

    private func remove(_ id: UUID) {
        lock.lock(); defer { lock.unlock() }
        subscribers.removeValue(forKey: id)
    }

    func publish(_ value: Result<AccessTokenResponse, Error>) {
        lock.lock()
        let continuations = Array(subscribers.values)
        lock.unlock()
        for continuation in continuations { continuation.yield(value) }
    }
}

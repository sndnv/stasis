import Foundation

public actor OAuthTokenManager {
    public static let defaultInitTimeout: TimeInterval = 5.0

    public struct NotInitialized: Error, Equatable {
        public init() {}
    }

    public struct InitTimeout: Error, Equatable {
        public let timeout: TimeInterval
        public init(timeout: TimeInterval) { self.timeout = timeout }
    }

    private struct Stored: Sendable {
        let underlying: AccessTokenResponse
        let expiresAt: Date
        let hasRefreshToken: Bool

        init(_ response: AccessTokenResponse, hasRefreshToken: Bool) {
            self.underlying = response
            self.expiresAt = Date().addingTimeInterval(TimeInterval(response.expiresIn))
            self.hasRefreshToken = hasRefreshToken
        }

        func isValid(withTolerance tolerance: TimeInterval) -> Bool {
            expiresAt > Date().addingTimeInterval(tolerance)
        }
    }

    private let oAuthClient: any OAuthClient
    private let onTokenUpdated: @Sendable (Result<AccessTokenResponse, Error>) -> Void
    private let expirationTolerance: TimeInterval
    private let initTimeout: TimeInterval

    private var stored: Result<Stored, Error> = .failure(NotInitialized())
    private var didInit: Bool = false

    public init(
        oAuthClient: any OAuthClient,
        onTokenUpdated: @escaping @Sendable (Result<AccessTokenResponse, Error>) -> Void,
        expirationTolerance: TimeInterval,
        initTimeout: TimeInterval = OAuthTokenManager.defaultInitTimeout
    ) {
        self.oAuthClient = oAuthClient
        self.onTokenUpdated = onTokenUpdated
        self.expirationTolerance = expirationTolerance
        self.initTimeout = initTimeout
    }

    public func token() async -> Result<AccessTokenResponse, Error> {
        await waitForInit()
        guard didInit else {
            return .failure(InitTimeout(timeout: initTimeout))
        }
        switch stored {
        case .failure(let error):
            return .failure(error)
        case .success(let snapshot):
            if snapshot.isValid(withTolerance: expirationTolerance) {
                return .success(snapshot.underlying)
            }
            if snapshot.hasRefreshToken, let refresh = snapshot.underlying.refreshToken {
                return await refreshWithToken(refresh, scope: snapshot.underlying.scope)
            }
            return await refreshWithClientCredentials(scope: snapshot.underlying.scope)
        }
    }

    public func configureWithRefreshToken(_ response: Result<AccessTokenResponse, Error>) {
        configure(response, hasRefreshToken: true)
    }

    public func configureWithClientCredentials(_ response: Result<AccessTokenResponse, Error>) {
        configure(response, hasRefreshToken: false)
    }

    public func reset() {
        let logout = ExplicitLogout()
        store(.failure(logout))
        onTokenUpdated(.failure(logout))
    }

    private func configure(_ response: Result<AccessTokenResponse, Error>, hasRefreshToken: Bool) {
        store(response.map { Stored($0, hasRefreshToken: hasRefreshToken) })
        onTokenUpdated(response)
    }

    private func store(_ result: Result<Stored, Error>) {
        stored = result
        didInit = true
    }

    private func waitForInit() async {
        if didInit { return }
        let deadline = ContinuousClock.now.advanced(by: .seconds(initTimeout))
        while !didInit && ContinuousClock.now < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    private func refreshWithToken(_ token: String, scope: String?) async -> Result<AccessTokenResponse, Error> {
        let refreshed = await oAuthClient.token(scope: scope, parameters: .refreshToken(token))
        switch refreshed {
        case .success(let response):
            let updated = AccessTokenResponse(
                accessToken: response.accessToken,
                refreshToken: response.refreshToken ?? token,
                expiresIn: response.expiresIn,
                scope: response.scope
            )
            stored = .success(Stored(updated, hasRefreshToken: true))
            onTokenUpdated(.success(updated))
            return .success(updated)
        case .failure(let error):
            stored = .failure(error)
            return .failure(error)
        }
    }

    private func refreshWithClientCredentials(scope: String?) async -> Result<AccessTokenResponse, Error> {
        let refreshed = await oAuthClient.token(scope: scope, parameters: .clientCredentials)
        switch refreshed {
        case .success(let response):
            stored = .success(Stored(response, hasRefreshToken: false))
            onTokenUpdated(.success(response))
        case .failure(let error):
            stored = .failure(error)
        }
        return refreshed
    }
}

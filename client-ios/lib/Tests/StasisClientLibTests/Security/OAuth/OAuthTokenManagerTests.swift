import Foundation
@testable import StasisClientLib
import Testing

@Suite("OAuthTokenManager")
struct OAuthTokenManagerTests {
    private let expiration: TimeInterval = 1
    private let tolerance: TimeInterval = 0.1
    private let initTimeout: TimeInterval = 0.25

    private var response: AccessTokenResponse {
        AccessTokenResponse(
            accessToken: "test-token",
            refreshToken: nil,
            expiresIn: Int64(expiration),
            scope: nil
        )
    }

    @Test("provides the latest available token (with refresh token)")
    func providesLatestWithRefreshToken() async {
        let updates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }

        let refreshToken = "refresh-token"
        let expected = AccessTokenResponse(
            accessToken: response.accessToken,
            refreshToken: refreshToken,
            expiresIn: response.expiresIn,
            scope: response.scope
        )

        let manager = OAuthTokenManager(
            oAuthClient: client,
            onTokenUpdated: { result in updates.append(result) },
            expirationTolerance: tolerance,
            initTimeout: initTimeout
        )

        await assertNotInitialized(manager)
        await manager.configureWithRefreshToken(.success(expected))
        await assertSuccess(manager, equals: expected)

        await eventually {
            let requests = await client.requests()
            let count = updates.count()
            let first = updates.successAt(0)
            return requests == 0 && count == 1 && first == expected
        }

        await manager.reset()
    }

    @Test("provides the latest available token (with client credentials)")
    func providesLatestWithClientCredentials() async {
        let updates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }

        let manager = OAuthTokenManager(
            oAuthClient: client,
            onTokenUpdated: { result in updates.append(result) },
            expirationTolerance: tolerance,
            initTimeout: initTimeout
        )

        await assertNotInitialized(manager)
        await manager.configureWithClientCredentials(.success(response))
        await assertSuccess(manager, equals: response)

        let expectedResponse = response
        await eventually {
            let requests = await client.requests()
            let count = updates.count()
            let first = updates.successAt(0)
            return requests == 0 && count == 1 && first == expectedResponse
        }

        await manager.reset()
    }

    @Test("retrieves new tokens when old ones expire (with refresh token)")
    func retrievesNewTokensWhenExpiredRefresh() async {
        let updates = ResultRecorder()

        let refreshToken = "refresh-token"
        let expiredResponse = AccessTokenResponse(
            accessToken: response.accessToken,
            refreshToken: refreshToken,
            expiresIn: 0,
            scope: response.scope
        )
        let validResponse = AccessTokenResponse(
            accessToken: response.accessToken,
            refreshToken: refreshToken,
            expiresIn: 42,
            scope: response.scope
        )

        let client = RecordingOAuthClient { .success(validResponse) }

        let manager = OAuthTokenManager(
            oAuthClient: client,
            onTokenUpdated: { result in updates.append(result) },
            expirationTolerance: tolerance,
            initTimeout: initTimeout
        )

        await assertNotInitialized(manager)
        await manager.configureWithRefreshToken(.success(expiredResponse))
        await assertSuccess(manager, equals: validResponse)

        await eventually {
            let requests = await client.requests()
            let count = updates.count()
            let first = updates.successAt(0)
            let second = updates.successAt(1)
            return requests == 1 && count == 2 && first == expiredResponse && second == validResponse
        }

        await assertSuccess(manager, equals: validResponse)
        await manager.reset()
    }

    @Test("retrieves new tokens when old ones expire (with client credentials)")
    func retrievesNewTokensWhenExpiredClientCredentials() async {
        let updates = ResultRecorder()

        let expiredResponse = AccessTokenResponse(
            accessToken: response.accessToken,
            refreshToken: nil,
            expiresIn: 0,
            scope: response.scope
        )
        let validResponse = AccessTokenResponse(
            accessToken: response.accessToken,
            refreshToken: nil,
            expiresIn: 42,
            scope: response.scope
        )

        let client = RecordingOAuthClient { .success(validResponse) }

        let manager = OAuthTokenManager(
            oAuthClient: client,
            onTokenUpdated: { result in updates.append(result) },
            expirationTolerance: tolerance,
            initTimeout: initTimeout
        )

        await assertNotInitialized(manager)
        await manager.configureWithClientCredentials(.success(expiredResponse))
        await assertSuccess(manager, equals: validResponse)

        await eventually {
            let requests = await client.requests()
            let count = updates.count()
            let first = updates.successAt(0)
            let second = updates.successAt(1)
            return requests == 1 && count == 2 && first == expiredResponse && second == validResponse
        }

        await assertSuccess(manager, equals: validResponse)
        await manager.reset()
    }

    @Test("fails if a valid token could not be retrieved (with refresh token)")
    func failsToRefreshWithRefreshToken() async {
        let updates = ResultRecorder()

        let refreshToken = "refresh-token"
        let expiredResponse = AccessTokenResponse(
            accessToken: response.accessToken,
            refreshToken: refreshToken,
            expiresIn: 0,
            scope: response.scope
        )

        let testError = TestError(message: "Test failure")
        let client = RecordingOAuthClient { .failure(testError) }

        let manager = OAuthTokenManager(
            oAuthClient: client,
            onTokenUpdated: { result in updates.append(result) },
            expirationTolerance: tolerance,
            initTimeout: initTimeout
        )

        await assertNotInitialized(manager)
        await manager.configureWithRefreshToken(.success(expiredResponse))
        await assertFailure(manager, matches: "Test failure")

        await eventually {
            let requests = await client.requests()
            let count = updates.count()
            let first = updates.successAt(0)
            return requests == 1 && count == 1 && first == expiredResponse
        }

        await assertFailure(manager, matches: "Test failure")
        await manager.reset()
    }

    @Test("fails if a valid token could not be retrieved (with client credentials)")
    func failsToRefreshWithClientCredentials() async {
        let updates = ResultRecorder()

        let expiredResponse = AccessTokenResponse(
            accessToken: response.accessToken,
            refreshToken: nil,
            expiresIn: 0,
            scope: response.scope
        )

        let testError = TestError(message: "Test failure")
        let client = RecordingOAuthClient { .failure(testError) }

        let manager = OAuthTokenManager(
            oAuthClient: client,
            onTokenUpdated: { result in updates.append(result) },
            expirationTolerance: tolerance,
            initTimeout: initTimeout
        )

        await assertNotInitialized(manager)
        await manager.configureWithClientCredentials(.success(expiredResponse))
        await assertFailure(manager, matches: "Test failure")

        await eventually {
            let requests = await client.requests()
            let count = updates.count()
            let first = updates.successAt(0)
            return requests == 1 && count == 1 && first == expiredResponse
        }

        await assertFailure(manager, matches: "Test failure")
        await manager.reset()
    }

    private func assertNotInitialized(_ manager: OAuthTokenManager) async {
        let result = await manager.token()
        guard case .failure(let error) = result, error is OAuthTokenManager.InitTimeout else {
            Issue.record("expected InitTimeout, got \(result)")
            return
        }
    }

    private func assertSuccess(_ manager: OAuthTokenManager, equals expected: AccessTokenResponse) async {
        let result = await manager.token()
        guard case .success(let actual) = result else {
            Issue.record("expected success, got \(result)")
            return
        }
        #expect(actual == expected)
    }

    private func assertFailure(_ manager: OAuthTokenManager, matches message: String) async {
        let result = await manager.token()
        guard case .failure(let error) = result else {
            Issue.record("expected failure, got \(result)")
            return
        }
        #expect(String(describing: error).contains(message))
    }

    private func eventually(
        timeout: Duration = .seconds(5),
        interval: Duration = .milliseconds(50),
        _ check: () async -> Bool
    ) async {
        let deadline = ContinuousClock.now.advanced(by: timeout)
        while ContinuousClock.now < deadline {
            if await check() { return }
            try? await Task.sleep(for: interval)
        }
        Issue.record("eventually condition did not become true within \(timeout)")
    }
}

private struct TestError: Error, CustomStringConvertible {
    let message: String
    var description: String { message }
}

private actor RecordingOAuthClient: OAuthClient {
    private let producer: @Sendable () -> Result<AccessTokenResponse, Error>
    private var count: Int = 0

    init(producer: @escaping @Sendable () -> Result<AccessTokenResponse, Error>) {
        self.producer = producer
    }

    func requests() -> Int { count }

    nonisolated func token(
        scope: String?,
        parameters: GrantParameters
    ) async -> Result<AccessTokenResponse, Error> {
        await record()
        return producer()
    }

    private func record() { count += 1 }
}

private final class ResultRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [Result<AccessTokenResponse, Error>] = []

    func append(_ entry: Result<AccessTokenResponse, Error>) {
        lock.lock(); defer { lock.unlock() }
        entries.append(entry)
    }

    func count() -> Int {
        lock.lock(); defer { lock.unlock() }
        return entries.count
    }

    func successAt(_ index: Int) -> AccessTokenResponse? {
        lock.lock(); defer { lock.unlock() }
        guard entries.indices.contains(index), case .success(let value) = entries[index] else {
            return nil
        }
        return value
    }
}

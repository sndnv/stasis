import Foundation
@testable import StasisClientLib
import Testing

@Suite("CredentialsProvider")
struct CredentialsProviderTests {
    private let config = CredentialsProvider.Config(
        coreScope: "core-scope",
        apiScope: "api-scope",
        expirationTolerance: 0.1
    )

    private let response = AccessTokenResponse(
        accessToken: "test-token",
        refreshToken: "refresh-token",
        expiresIn: 1,
        scope: nil
    )

    private let secret = DeviceSecret(
        user: UUID(),
        device: UUID(),
        secret: Data("test-secret".utf8),
        target: SecretsConfigFixtures.testConfig
    )

    private let hashedPassword: UserAuthenticationPassword = .hashed(
        user: UUID(),
        hashedPassword: Data("test-password".utf8)
    )

    @Test("supports initializing with existing token responses (valid)")
    func initializesWithValidTokenResponses() async {
        let coreUpdates = ResultRecorder()
        let apiUpdates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let coreToken = AccessTokenResponse(
            accessToken: CredentialsTestJwt.create(expiresInSeconds: 60),
            refreshToken: nil,
            expiresIn: response.expiresIn,
            scope: response.scope
        )
        let apiToken = AccessTokenResponse(
            accessToken: CredentialsTestJwt.create(expiresInSeconds: 60),
            refreshToken: nil,
            expiresIn: response.expiresIn,
            scope: response.scope
        )

        let coreSub = drainTask(provider.coreTokenUpdates(), into: coreUpdates)
        let apiSub = drainTask(provider.apiTokenUpdates(), into: apiUpdates)

        await provider.initialize(
            coreToken: coreToken,
            apiToken: apiToken,
            plaintextDeviceSecret: secret.secret,
            digestedUserPassword: "test-password"
        )

        await eventually {
            let requests = await client.requests()
            let coreCount = coreUpdates.count()
            let apiCount = apiUpdates.count()
            let coreFirst = coreUpdates.successAt(0)
            let apiFirst = apiUpdates.successAt(0)
            let core = await provider.core()
            let api = await provider.api()
            let device = await provider.currentDeviceSecret()
            return requests == 0
                && coreCount == 1 && apiCount == 1
                && coreFirst?.accessToken == coreToken.accessToken
                && apiFirst?.accessToken == apiToken.accessToken
                && (try? core.get())?.accessToken == coreToken.accessToken
                && (try? api.get())?.accessToken == apiToken.accessToken
                && (try? device.get()) == self.secret
        }

        coreSub.cancel(); apiSub.cancel()
        await provider.logout()
    }

    @Test("supports initializing with existing token responses (expired / with refresh tokens)")
    func initializesWithExpiredTokensAndRefreshToken() async {
        let coreUpdates = ResultRecorder()
        let apiUpdates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let coreToken = AccessTokenResponse(
            accessToken: CredentialsTestJwt.create(expiresInSeconds: -60),
            refreshToken: response.refreshToken,
            expiresIn: response.expiresIn,
            scope: response.scope
        )
        let apiToken = AccessTokenResponse(
            accessToken: CredentialsTestJwt.create(expiresInSeconds: -60),
            refreshToken: response.refreshToken,
            expiresIn: response.expiresIn,
            scope: response.scope
        )

        let coreSub = drainTask(provider.coreTokenUpdates(), into: coreUpdates)
        let apiSub = drainTask(provider.apiTokenUpdates(), into: apiUpdates)

        await provider.initialize(
            coreToken: coreToken,
            apiToken: apiToken,
            plaintextDeviceSecret: secret.secret,
            digestedUserPassword: "test-password"
        )

        await eventually {
            let requests = await client.requests()
            let coreCount = coreUpdates.count()
            let apiCount = apiUpdates.count()
            let coreFirst = coreUpdates.successAt(0)
            let apiFirst = apiUpdates.successAt(0)
            let core = await provider.core()
            let api = await provider.api()
            let device = await provider.currentDeviceSecret()
            return requests == 2
                && coreCount == 1 && apiCount == 1
                && coreFirst == self.response && apiFirst == self.response
                && (try? core.get()) == self.response
                && (try? api.get()) == self.response
                && (try? device.get()) == self.secret
        }

        coreSub.cancel(); apiSub.cancel()
        await provider.logout()
    }

    @Test("supports initializing with existing token responses (expired / without refresh tokens)")
    func initializesWithExpiredTokensAndNoRefreshToken() async {
        let coreUpdates = ResultRecorder()
        let apiUpdates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let coreToken = AccessTokenResponse(
            accessToken: CredentialsTestJwt.create(expiresInSeconds: -60),
            refreshToken: nil,
            expiresIn: response.expiresIn,
            scope: response.scope
        )
        let apiToken = AccessTokenResponse(
            accessToken: CredentialsTestJwt.create(expiresInSeconds: -60),
            refreshToken: nil,
            expiresIn: response.expiresIn,
            scope: response.scope
        )

        let coreSub = drainTask(provider.coreTokenUpdates(), into: coreUpdates)
        let apiSub = drainTask(provider.apiTokenUpdates(), into: apiUpdates)

        await provider.initialize(
            coreToken: coreToken,
            apiToken: apiToken,
            plaintextDeviceSecret: secret.secret,
            digestedUserPassword: "test-password"
        )

        await eventually {
            let requests = await client.requests()
            let coreCount = coreUpdates.count()
            let apiCount = apiUpdates.count()
            let coreFirstFailedAsTokenExpired = (coreUpdates.failureAt(0) is TokenExpired)
            let apiFirstFailedAsTokenExpired = (apiUpdates.failureAt(0) is TokenExpired)
            let core = await provider.core()
            let api = await provider.api()
            let device = await provider.currentDeviceSecret()
            let coreIsTokenExpired = (try? core.get()) == nil
                && (resultError(core) is TokenExpired)
            let apiIsTokenExpired = (try? api.get()) == nil
                && (resultError(api) is TokenExpired)
            let deviceIsMissing = (try? device.get()) == nil
                && (resultError(device) is MissingDeviceSecret)
            return requests == 0
                && coreCount == 1 && apiCount == 1
                && coreFirstFailedAsTokenExpired && apiFirstFailedAsTokenExpired
                && coreIsTokenExpired && apiIsTokenExpired && deviceIsMissing
        }

        coreSub.cancel(); apiSub.cancel()
        await provider.logout()
    }

    @Test("supports initializing with existing refresh tokens")
    func initializesWithExistingRefreshToken() async {
        let coreUpdates = ResultRecorder()
        let apiUpdates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let coreSub = drainTask(provider.coreTokenUpdates(), into: coreUpdates)
        let apiSub = drainTask(provider.apiTokenUpdates(), into: apiUpdates)

        await provider.initialize(
            apiRefreshToken: "api-token",
            plaintextDeviceSecret: secret.secret,
            digestedUserPassword: "test-password"
        )

        await eventually {
            let requests = await client.requests()
            let coreCount = coreUpdates.count()
            let apiCount = apiUpdates.count()
            let coreFirst = coreUpdates.successAt(0)
            let apiFirst = apiUpdates.successAt(0)
            let core = await provider.core()
            let api = await provider.api()
            let device = await provider.currentDeviceSecret()
            return requests == 2
                && coreCount == 1 && apiCount == 1
                && coreFirst == self.response && apiFirst == self.response
                && (try? core.get()) == self.response
                && (try? api.get()) == self.response
                && (try? device.get()) == self.secret
        }

        coreSub.cancel(); apiSub.cancel()
        await provider.logout()
    }

    @Test("supports logging in with a username and password")
    func supportsLoggingIn() async {
        let coreUpdates = ResultRecorder()
        let apiUpdates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let coreSub = drainTask(provider.coreTokenUpdates(), into: coreUpdates)
        let apiSub = drainTask(provider.apiTokenUpdates(), into: apiUpdates)

        let loginResult = await provider.login(username: "user", password: "password")

        await eventually {
            let requests = await client.requests()
            let coreCount = coreUpdates.count()
            let apiCount = apiUpdates.count()
            let coreFirst = coreUpdates.successAt(0)
            let apiFirst = apiUpdates.successAt(0)
            let core = await provider.core()
            let api = await provider.api()
            let device = await provider.currentDeviceSecret()
            return requests == 2
                && (try? loginResult.get()) != nil
                && coreCount == 1 && apiCount == 1
                && coreFirst == self.response && apiFirst == self.response
                && (try? core.get()) == self.response
                && (try? api.get()) == self.response
                && (try? device.get()) == self.secret
        }

        coreSub.cancel(); apiSub.cancel()
        await provider.logout()
    }

    @Test("fails to log in if the device's secret cannot be decrypted")
    func failsLoginOnSecretDecryptionFailure() async {
        let coreUpdates = ResultRecorder()
        let apiUpdates = ResultRecorder()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        store.loadDeviceSecretHandler = { _ in
            .failure(TestError(message: "Test failure"))
        }
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let coreSub = drainTask(provider.coreTokenUpdates(), into: coreUpdates)
        let apiSub = drainTask(provider.apiTokenUpdates(), into: apiUpdates)

        let loginResult = await provider.login(username: "user", password: "password")

        await eventually {
            let requests = await client.requests()
            let coreCount = coreUpdates.count()
            let apiCount = apiUpdates.count()
            let device = await provider.currentDeviceSecret()
            return requests == 0
                && loginIsFailure(loginResult)
                && coreCount == 0 && apiCount == 0
                && resultErrorMessage(device) == "Test failure"
        }

        coreSub.cancel(); apiSub.cancel()
    }

    @Test("supports verifying user passwords")
    func supportsVerifyingPasswords() async {
        let currentPassword = "test-password"
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        store.verifyUserPasswordHandler = { input in input == currentPassword }

        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let result = await provider.verifyUserPassword(currentPassword)
        #expect(result == true)
    }

    @Test("supports updating user credentials")
    func supportsUpdatingUserCredentials() async {
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let mockApi = MockServerApiEndpointClient()
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let before = await mockApi.calls.userPasswordUpdated
        #expect(before == 0)

        let result = await provider.updateUserCredentials(
            api: mockApi,
            currentPassword: "current-password",
            newPassword: "new-password",
            newSalt: "test-salt"
        )
        #expect((try? result.get()) != nil)

        await eventually {
            let after = await mockApi.calls.userPasswordUpdated
            return after == 1
        }
    }

    @Test("supports updating the device's secret")
    func supportsUpdatingDeviceSecret() async {
        let otherSecret = DeviceSecret(
            user: UUID(),
            device: UUID(),
            secret: Data("other-test-secret".utf8),
            target: SecretsConfigFixtures.testConfig
        )
        let secretUpdated = AtomicBool()

        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        store.storeDeviceSecretHandler = { _, _ in
            secretUpdated.set(true)
            return .success(otherSecret)
        }

        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let result = await provider.updateDeviceSecret(
            plaintextDeviceSecret: otherSecret.secret,
            password: "test-password"
        )

        await eventually {
            let device = await provider.currentDeviceSecret()
            return secretUpdated.get()
                && (try? result.get()) == otherSecret
                && (try? device.get()) == otherSecret
        }
    }

    @Test("supports pushing the device's secret")
    func supportsPushingDeviceSecret() async {
        let secretPushed = AtomicBool()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        store.pushDeviceSecretHandler = { _, _, _ in
            secretPushed.set(true)
            return .success(())
        }

        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let result = await provider.pushDeviceSecret(
            api: MockServerApiEndpointClient(),
            password: "test-password",
            remotePassword: nil
        )

        await eventually {
            return secretPushed.get() && (try? result.get()) != nil
        }
    }

    @Test("supports pulling the device's secret")
    func supportsPullingDeviceSecret() async {
        let secretPulled = AtomicBool()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        store.pullDeviceSecretHandler = { _, _, _ in
            secretPulled.set(true)
            return .success(self.secret)
        }

        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let before = await provider.currentDeviceSecret()
        #expect(resultError(before) is MissingDeviceSecret)

        let result = await provider.pullDeviceSecret(
            api: MockServerApiEndpointClient(),
            password: "test-password",
            remotePassword: nil
        )

        await eventually {
            let device = await provider.currentDeviceSecret()
            return secretPulled.get()
                && (try? result.get()) != nil
                && (try? device.get()) == self.secret
        }
    }

    @Test("does not update the device's secret if the pull failed")
    func doesNotUpdateOnPullFailure() async {
        let secretPulled = AtomicBool()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        store.pullDeviceSecretHandler = { _, _, _ in
            secretPulled.set(true)
            return .failure(TestError(message: "Test failure"))
        }

        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let before = await provider.currentDeviceSecret()
        #expect(resultError(before) is MissingDeviceSecret)

        let result = await provider.pullDeviceSecret(
            api: MockServerApiEndpointClient(),
            password: "test-password",
            remotePassword: nil
        )

        await eventually {
            let device = await provider.currentDeviceSecret()
            return secretPulled.get()
                && (try? result.get()) == nil
                && resultError(device) is MissingDeviceSecret
        }
    }

    @Test("supports re-encrypting the device's secret")
    func supportsReEncryptingDeviceSecret() async {
        let secretReEncrypted = AtomicBool()
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        store.reEncryptDeviceSecretHandler = { _, _ in
            secretReEncrypted.set(true)
            return .success(())
        }

        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let result = await provider.reEncryptDeviceSecret(
            currentPassword: "test-password",
            oldPassword: "other-password"
        )

        await eventually {
            return secretReEncrypted.get() && (try? result.get()) != nil
        }
    }

    @Test("exposes core and api credentials providers backed by current tokens")
    func exposesHttpCredentialsProviders() async {
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)

        let coreCredentials = provider.coreCredentialsProvider()
        let apiCredentials = provider.apiCredentialsProvider()

        _ = await provider.login(username: "user", password: "password")

        await eventually {
            let core = await coreCredentials.credentials()
            let api = await apiCredentials.credentials()
            return core == .oauth2BearerToken(token: self.response.accessToken)
                && api == .oauth2BearerToken(token: self.response.accessToken)
        }

        await provider.logout()

        await eventually {
            let core = await coreCredentials.credentials()
            let api = await apiCredentials.credentials()
            return core == .none && api == .none
        }
    }

    @Test("supports checking if the device's remote secret exists")
    func supportsCheckingRemoteSecretExists() async {
        let client = RecordingOAuthClient { .success(self.response) }
        let store = MockCredentialsStore(
            deviceSecret: secret, authenticationPassword: hashedPassword
        )
        let provider = CredentialsProvider(config: config, oAuthClient: client, store: store)
        let api = MockServerApiEndpointClient()

        let result = await provider.remoteDeviceSecretExists(api: api)

        await eventually {
            let exists = await api.calls.deviceKeyExistsChecked
            let pulled = await api.calls.deviceKeyPulled
            let pushed = await api.calls.deviceKeyPushed
            return exists == 1 && pulled == 0 && pushed == 0 && (try? result.get()) != nil
        }
    }

    private func drainTask(
        _ stream: AsyncStream<Result<AccessTokenResponse, Error>>,
        into recorder: ResultRecorder
    ) -> Task<Void, Never> {
        Task {
            for await update in stream {
                recorder.append(update)
            }
        }
    }

    private func resultError<T>(_ result: Result<T, Error>) -> Error? {
        if case .failure(let error) = result { return error }
        return nil
    }

    private func resultErrorMessage<T>(_ result: Result<T, Error>) -> String? {
        guard let error = resultError(result) else { return nil }
        return (error as? TestError)?.message ?? String(describing: error)
    }

    private func loginIsFailure(_ result: Result<(DeviceSecret, String), Error>) -> Bool {
        if case .failure = result { return true }
        return false
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

    func failureAt(_ index: Int) -> Error? {
        lock.lock(); defer { lock.unlock() }
        guard entries.indices.contains(index), case .failure(let error) = entries[index] else {
            return nil
        }
        return error
    }
}

private final class AtomicBool: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Bool = false

    func set(_ newValue: Bool) {
        lock.lock(); defer { lock.unlock() }
        value = newValue
    }

    func get() -> Bool {
        lock.lock(); defer { lock.unlock() }
        return value
    }
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

private enum CredentialsTestJwt {
    static func create(expiresInSeconds: TimeInterval) -> String {
        let exp = Date().timeIntervalSince1970 + expiresInSeconds
        return create(payload: ["sub": "test-subject", "exp": exp])
    }

    static func create(payload: [String: Any]) -> String {
        let header: [String: Any] = ["alg": "none", "typ": "JWT"]
        let headerData = (try? JSONSerialization.data(withJSONObject: header)) ?? Data()
        let payloadData = (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
        let signature = Data("fake-sig".utf8)
        return [
            headerData.base64UrlEncodedString(),
            payloadData.base64UrlEncodedString(),
            signature.base64UrlEncodedString()
        ].joined(separator: ".")
    }
}

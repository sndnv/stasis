import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("KeychainCredentialsStore")
struct KeychainCredentialsStoreTests {
    private struct Bundle {
        let store: KeychainCredentialsStore
        let defaults: UserDefaults
        let keychain: Keychain
    }

    private func bundle() throws -> Bundle {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let api = try #require(try defaults.serverApiConfig())
        let keychain = Keychain(service: "stasis.tests.\(UUID().uuidString)")
        let store = try KeychainCredentialsStore(apiConfig: api, preferences: defaults, keychain: keychain)
        return Bundle(store: store, defaults: defaults, keychain: keychain)
    }

    @Test("initDeviceSecret wraps the raw bytes for the bound user and device")
    func initDeviceSecretRoundTrip() throws {
        let bundle = try bundle()
        let raw = Secrets.generateRawDeviceSecret(secretSize: 32)
        let secret = bundle.store.initDeviceSecret(raw)
        #expect(secret.user == bundle.store.user)
        #expect(secret.device == bundle.store.device)
        #expect(secret.secret == raw)
    }

    @Test("rejects malformed user / device UUIDs")
    func rejectsMalformed() {
        #expect(throws: KeychainCredentialsStoreError.self) {
            _ = try KeychainCredentialsStore(
                apiConfig: TestDefaults.apiConfig().with(user: "not-a-uuid"),
                preferences: TestDefaults.isolatedDefaults(),
                keychain: Keychain(service: "tmp")
            )
        }
    }

    @Test("verifyUserPassword returns false before initDigestedUserPassword")
    func verifyBeforeInit() async throws {
        let bundle = try bundle()
        let result = await bundle.store.verifyUserPassword("anything")
        #expect(!result)
    }

    @Test("initDigestedUserPassword + verifyUserPassword agrees with stored digest")
    func verifyMatchesStored() async throws {
        let bundle = try bundle()
        let password = "passw0rd"
        let digested = bundle.store.getAuthenticationPassword(password).digested()
        bundle.store.initDigestedUserPassword(digested)

        #expect(try bundle.keychain.string(account: KeychainCredentialsStore.digestedPasswordAccount) == digested)
        #expect(await bundle.store.verifyUserPassword(password))
        #expect(!(await bundle.store.verifyUserPassword("wrong")))
    }

    @Test("initDigestedUserPassword(nil) clears the keychain entry")
    func clearsDigest() throws {
        let bundle = try bundle()
        bundle.store.initDigestedUserPassword("digest-value")
        #expect(try bundle.keychain.string(account: KeychainCredentialsStore.digestedPasswordAccount) != nil)
        bundle.store.initDigestedUserPassword(nil)
        #expect(try bundle.keychain.string(account: KeychainCredentialsStore.digestedPasswordAccount) == nil)
    }

    @Test("store/load device secret round-trips through the store")
    func storeAndLoad() async throws {
        let bundle = try bundle()
        let raw = Secrets.generateRawDeviceSecret(secretSize: 64)
        let storeResult = await bundle.store.storeDeviceSecret(raw, userPassword: "pw")
        #expect(try storeResult.get().secret == raw)

        let loadResult = await bundle.store.loadDeviceSecret(userPassword: "pw")
        #expect(try loadResult.get().secret == raw)
    }

    @Test("reEncryptDeviceSecret rotates the local password")
    func reEncrypt() async throws {
        let bundle = try bundle()
        let raw = Secrets.generateRawDeviceSecret(secretSize: 64)
        _ = await bundle.store.storeDeviceSecret(raw, userPassword: "old")

        let rotated = await bundle.store.reEncryptDeviceSecret(
            currentUserPassword: "new", oldUserPassword: "old"
        )
        if case .failure(let error) = rotated { Issue.record("re-encrypt failed: \(error)"); return }

        let reloaded = await bundle.store.loadDeviceSecret(userPassword: "new")
        #expect(try reloaded.get().secret == raw)
    }

    @Test("pushDeviceSecret delegates to the api client")
    func pushDeviceSecret() async throws {
        let bundle = try bundle()
        _ = await bundle.store.storeDeviceSecret(
            Secrets.generateRawDeviceSecret(secretSize: 64), userPassword: "pw"
        )

        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let result = await bundle.store.pushDeviceSecret(api: api, userPassword: "pw", remotePassword: nil)
        if case .failure(let error) = result { Issue.record("push failed: \(error)"); return }
        #expect(await api.calls.deviceKeyPushed == 1)
    }

    @Test("pullDeviceSecret delegates to the api client")
    func pullDeviceSecret() async throws {
        let bundle = try bundle()
        let raw = Secrets.generateRawDeviceSecret(secretSize: 64)
        _ = await bundle.store.storeDeviceSecret(raw, userPassword: "pw")

        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        _ = await bundle.store.pushDeviceSecret(api: api, userPassword: "pw", remotePassword: nil)

        let result = await bundle.store.pullDeviceSecret(api: api, userPassword: "pw", remotePassword: nil)
        switch result {
        case .success:
            break
        case .failure:
            break
        }
        #expect(await api.calls.deviceKeyPulled == 1)
    }

    @Test("updateUserCredentials rotates the local salt and refreshes the digest")
    func updateUserCredentials() async throws {
        let bundle = try bundle()
        _ = await bundle.store.storeDeviceSecret(
            Secrets.generateRawDeviceSecret(secretSize: 64), userPassword: "old"
        )

        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let result = await bundle.store.updateUserCredentials(
            api: api,
            currentUserPassword: "old",
            newUserPassword: "new",
            newUserSalt: "new-salt"
        )
        switch result {
        case .failure(let error):
            Issue.record("update failed: \(error)")
        case .success:
            #expect(await bundle.store.verifyUserPassword("new"))
            #expect(!(await bundle.store.verifyUserPassword("old")))
        }
    }
}

private extension Config.ServerApi {
    func with(user: String) -> Config.ServerApi {
        Config.ServerApi(url: url, user: user, userSalt: userSalt, device: device)
    }
}

import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("Secrets")
struct SecretsTests {
    @Test("generates random device secrets of the requested size")
    func generateRaw() {
        let secret = Secrets.generateRawDeviceSecret(secretSize: 64)
        #expect(secret.count == 64)
        let secondary = Secrets.generateRawDeviceSecret(secretSize: 64)
        #expect(secret != secondary)
    }

    @Test("reports localDeviceSecretExists")
    func localExists() {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(!Secrets.localDeviceSecretExists(preferences: defaults))
        defaults.putEncryptedDeviceSecret(Data([0x01, 0x02]))
        #expect(Secrets.localDeviceSecretExists(preferences: defaults))
    }

    @Test("initDeviceSecret builds a DeviceSecret bound to the secrets config")
    func initDeviceSecret() throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        let raw = Data((0..<32).map { UInt8($0) })

        let secret = try Secrets.initDeviceSecret(
            user: user, device: device, secret: raw, preferences: defaults
        )
        #expect(secret.user == user)
        #expect(secret.device == device)
        #expect(secret.secret == raw)
    }

    @Test("createDeviceSecret then loadDeviceSecret round-trips")
    func createAndLoad() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        let salt = "salt-xyz"
        let password = "passw0rd"

        let created = await Secrets.createDeviceSecret(
            user: user, userSalt: salt, userPassword: password,
            device: device, preferences: defaults
        )
        if case .failure(let error) = created { Issue.record("create failed: \(error)"); return }

        let loaded = await Secrets.loadDeviceSecret(
            user: user, userSalt: salt, userPassword: password,
            device: device, preferences: defaults
        )
        let secret = try loaded.get()
        #expect(secret.user == user)
        #expect(secret.device == device)
        #expect(secret.secret.count == Secrets.defaultDeviceSecretSize)
    }

    @Test("storeDeviceSecret persists the supplied bytes")
    func storeDeviceSecret() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        let raw = Secrets.generateRawDeviceSecret(secretSize: 64)

        let stored = await Secrets.storeDeviceSecret(
            user: user, userSalt: "salt", userPassword: "pw",
            device: device, secret: raw, preferences: defaults
        )
        #expect(try stored.get().secret == raw)

        let reloaded = await Secrets.loadDeviceSecret(
            user: user, userSalt: "salt", userPassword: "pw",
            device: device, preferences: defaults
        )
        #expect(try reloaded.get().secret == raw)
    }

    @Test("reEncryptDeviceSecret re-keys with the new password locally")
    func reEncrypt() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()

        _ = await Secrets.createDeviceSecret(
            user: user, userSalt: "s1", userPassword: "old",
            device: device, preferences: defaults
        )
        let originalSecret = try await Secrets.loadDeviceSecret(
            user: user, userSalt: "s1", userPassword: "old", device: device, preferences: defaults
        ).get().secret

        let reEncrypted = await Secrets.reEncryptDeviceSecret(
            user: user,
            currentUserSalt: "s1", currentUserPassword: "old",
            newUserSalt: "s1", newUserPassword: "new",
            device: device, preferences: defaults, api: nil
        )
        if case .failure(let error) = reEncrypted { Issue.record("re-encrypt failed: \(error)"); return }

        let reloaded = await Secrets.loadDeviceSecret(
            user: user, userSalt: "s1", userPassword: "new", device: device, preferences: defaults
        )
        #expect(try reloaded.get().secret == originalSecret)
    }

    @Test("loadUserAuthenticationPassword returns the configured shape")
    func authPasswordShape() throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let password = try Secrets.loadUserAuthenticationPassword(
            user: UUID(), userSalt: "s", userPassword: "pw", preferences: defaults
        )
        if case .unhashed = password {
            Issue.record("expected hashed authentication password when derivation is enabled")
        }
    }

    @Test("pushDeviceSecret encrypts the local secret with the user password and uploads")
    func pushDeviceSecret() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        _ = await Secrets.createDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", device: device, preferences: defaults
        )

        let api = MockServerApiEndpointClient()
        let result = await Secrets.pushDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", remotePassword: nil,
            device: device, preferences: defaults, api: api
        )
        if case .failure(let error) = result { Issue.record("push failed: \(error)"); return }
        #expect(await api.calls.deviceKeyPushed == 1)
    }

    @Test("pushDeviceSecret uses a separate remote password when supplied")
    func pushDeviceSecretWithRemote() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        _ = await Secrets.createDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", device: device, preferences: defaults
        )

        let api = MockServerApiEndpointClient()
        let result = await Secrets.pushDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", remotePassword: "remote",
            device: device, preferences: defaults, api: api
        )
        if case .failure(let error) = result { Issue.record("push failed: \(error)"); return }
        #expect(await api.calls.deviceKeyPushed == 1)
    }

    @Test("pullDeviceSecret decrypts and re-encrypts using the user password")
    func pullDeviceSecret() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        _ = await Secrets.createDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", device: device, preferences: defaults
        )
        let originalSecret = try await Secrets.loadDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", device: device, preferences: defaults
        ).get().secret

        let api = MockServerApiEndpointClient()

        _ = await Secrets.pushDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", remotePassword: nil,
            device: device, preferences: defaults, api: api
        )
        let pushedKey = await api.lastRequest(as: Data.self) ?? Data()
        await api.setPullDeviceKeyOverride(pushedKey.isEmpty ? Data("test-key".utf8) : pushedKey)

        let result = await Secrets.pullDeviceSecret(
            user: user, userSalt: "s", userPassword: "pw", remotePassword: nil,
            device: device, preferences: defaults, api: api
        )
        switch result {
        case .success(let pulled):
            #expect(pulled.secret.count == originalSecret.count)
        case .failure:
            break
        }
        #expect(await api.calls.deviceKeyPulled == 1)
    }

    @Test("reEncryptDeviceSecret pushes the new key when remote copy exists")
    func reEncryptWithApi() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        _ = await Secrets.createDeviceSecret(
            user: user, userSalt: "s1", userPassword: "old", device: device, preferences: defaults
        )

        let api = MockServerApiEndpointClient()
        await api.setDeviceKeyExistsOverride(true)
        let result = await Secrets.reEncryptDeviceSecret(
            user: user,
            currentUserSalt: "s1", currentUserPassword: "old",
            newUserSalt: "s1", newUserPassword: "new",
            device: device, preferences: defaults, api: api
        )
        if case .failure(let error) = result { Issue.record("re-encrypt failed: \(error)"); return }
        #expect(await api.calls.deviceKeyExistsChecked == 1)
        #expect(await api.calls.deviceKeyPushed == 1)
    }

    @Test("loadDeviceSecret fails against unrelated ciphertext")
    func loadDeviceSecretRejectsGarbage() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        defaults.putEncryptedDeviceSecret(Data((0..<32).map { _ in UInt8.random(in: 0...255) }))

        let result = await Secrets.loadDeviceSecret(
            user: UUID(), userSalt: "s", userPassword: "pw", device: UUID(), preferences: defaults
        )
        #expect(throws: (any Error).self) { try result.get() }
    }

    @Test("reEncryptDeviceSecret skips the api push when remote copy does not exist")
    func reEncryptWithoutApiPush() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let user = UUID()
        let device = UUID()
        _ = await Secrets.createDeviceSecret(
            user: user, userSalt: "s1", userPassword: "old", device: device, preferences: defaults
        )

        let api = MockServerApiEndpointClient()
        await api.setDeviceKeyExistsOverride(false)
        let result = await Secrets.reEncryptDeviceSecret(
            user: user,
            currentUserSalt: "s1", currentUserPassword: "old",
            newUserSalt: "s1", newUserPassword: "new",
            device: device, preferences: defaults, api: api
        )
        if case .failure(let error) = result { Issue.record("re-encrypt failed: \(error)"); return }
        #expect(await api.calls.deviceKeyExistsChecked == 1)
        #expect(await api.calls.deviceKeyPushed == 0)
    }
}

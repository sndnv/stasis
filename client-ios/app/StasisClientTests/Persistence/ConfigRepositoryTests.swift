import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("ConfigRepository")
struct ConfigRepositoryTests {
    @Test("reports availability after bootstrap")
    func availableAfterBootstrap() throws {
        let repo = ConfigRepository(preferences: TestDefaults.isolatedDefaults())
        #expect(try !repo.available())

        let params = TestDefaults.bootstrapParams()
        repo.bootstrap(params: params)
        #expect(try repo.available())
    }

    @Test("reads back authentication / api / core config")
    func readsBack() throws {
        let defaults = TestDefaults.isolatedDefaults()
        let repo = ConfigRepository(preferences: defaults)
        let params = TestDefaults.bootstrapParams()
        repo.bootstrap(params: params)

        let auth = try #require(try defaults.authenticationConfig())
        #expect(auth.tokenEndpoint == params.authentication.tokenEndpoint)
        #expect(auth.clientId == params.authentication.clientId)
        #expect(auth.clientSecret == params.authentication.clientSecret)
        #expect(auth.scopeApi == params.authentication.scopes.api)
        #expect(auth.scopeCore == params.authentication.scopes.core)

        let api = try #require(try defaults.serverApiConfig())
        #expect(api.url == params.serverApi.url)
        #expect(api.user == params.serverApi.user)

        let core = try #require(try defaults.serverCoreConfig())
        #expect(core.address == params.serverCore.address)
        #expect(core.nodeId == params.serverCore.nodeId)
    }

    @Test("flags malformed authentication config")
    func malformedAuth() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("only-one", forKey: ConfigRepository.Keys.Authentication.tokenEndpoint)
        #expect(throws: ConfigRepository.RepositoryError.self) {
            _ = try defaults.authenticationConfig()
        }
    }

    @Test("reset clears all known keys")
    func resetClears() throws {
        let defaults = TestDefaults.isolatedDefaults()
        let repo = ConfigRepository(preferences: defaults)
        repo.bootstrap(params: TestDefaults.bootstrapParams())
        defaults.firstRunComplete()
        defaults.saveUsername("user-a")
        defaults.saveLastProcessedCommand(7)
        defaults.putAnalyticsCachedEntry("payload")

        repo.reset()

        #expect(try !repo.available())
        #expect(defaults.isFirstRun() == ConfigRepository.Defaults.General.isFirstRun)
        #expect(defaults.savedUsername() == nil)
        #expect(defaults.savedLastProcessedCommand() == 0)
        #expect(defaults.analyticsCachedEntry() == nil)
    }

    @Test("device secret round-trips through base64")
    func deviceSecretRoundTrip() throws {
        let defaults = TestDefaults.isolatedDefaults()
        let secret = Data([0x01, 0x02, 0x03, 0x04, 0x05])
        defaults.putEncryptedDeviceSecret(secret)
        #expect(try defaults.encryptedDeviceSecret() == secret)
    }

    @Test("device secret read fails when missing")
    func deviceSecretMissing() {
        #expect(throws: ConfigRepository.RepositoryError.self) {
            _ = try TestDefaults.isolatedDefaults().encryptedDeviceSecret()
        }
    }

    @Test("first-run flag flips after firstRunComplete")
    func firstRunFlag() {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(defaults.isFirstRun())
        defaults.firstRunComplete()
        #expect(!defaults.isFirstRun())
    }

    @Test("derives secrets config from defaults when unset")
    func defaultSecretsConfig() throws {
        let defaults = TestDefaults.isolatedDefaults()
        let config = try defaults.secretsConfig()
        #expect(config.derivation.encryption.iterations
            == ConfigRepository.Defaults.Secrets.Derivation.Encryption.iterations)
        #expect(config.encryption.file.keySize
            == ConfigRepository.Defaults.Secrets.Encryption.File.keySize)
        #expect(config.encryption.file.ivSize == Aes.ivSize)
    }

    @Test("derives secrets config from bootstrap values")
    func bootstrapSecretsConfig() throws {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        let config = try defaults.secretsConfig()
        #expect(config.derivation.encryption.saltPrefix == "enc")
        #expect(config.derivation.authentication.saltPrefix == "auth")
    }

    @Test("authenticationConfig returns nil when no keys are set")
    func missingAuthenticationConfig() throws {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(try defaults.authenticationConfig() == nil)
    }

    @Test("serverApiConfig returns nil when no keys are set")
    func missingServerApiConfig() throws {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(try defaults.serverApiConfig() == nil)
    }

    @Test("serverCoreConfig returns nil when no keys are set")
    func missingServerCoreConfig() throws {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(try defaults.serverCoreConfig() == nil)
    }

    @Test("flags malformed serverApi config")
    func malformedServerApi() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("only-one", forKey: ConfigRepository.Keys.ServerApi.url)
        #expect(throws: ConfigRepository.RepositoryError.self) {
            _ = try defaults.serverApiConfig()
        }
    }

    @Test("flags malformed serverCore config")
    func malformedServerCore() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("only-one", forKey: ConfigRepository.Keys.ServerCore.address)
        #expect(throws: ConfigRepository.RepositoryError.self) {
            _ = try defaults.serverCoreConfig()
        }
    }

    @Test("savedUsername round-trips")
    func savedUsername() {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(defaults.savedUsername() == nil)
        defaults.saveUsername("user-a")
        #expect(defaults.savedUsername() == "user-a")
        defaults.saveUsername(nil)
        #expect(defaults.savedUsername() == nil)
    }

    @Test("savedLastProcessedCommand defaults to zero")
    func savedLastProcessedCommandDefault() {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(defaults.savedLastProcessedCommand() == 0)
    }

    @Test("savedLastProcessedCommand round-trips")
    func savedLastProcessedCommand() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.saveLastProcessedCommand(42)
        #expect(defaults.savedLastProcessedCommand() == 42)
    }

    @Test("analyticsCachedEntry round-trips")
    func analyticsCachedEntry() {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(defaults.analyticsCachedEntry() == nil)
        defaults.putAnalyticsCachedEntry("payload")
        #expect(defaults.analyticsCachedEntry() == "payload")
    }
}

@Suite("RepositoryError")
struct RepositoryErrorTests {
    @Test("describes each case")
    func messages() {
        #expect(ConfigRepository.RepositoryError.malformedConfig("test a").errorDescription == "Malformed configuration: test a")
        #expect(ConfigRepository.RepositoryError.missingDeviceSecret.errorDescription == "No device secret is available")
    }
}

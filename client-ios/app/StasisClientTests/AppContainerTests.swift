import Foundation
@testable import StasisClient
import Testing

@MainActor
@Suite("AppContainer")
struct AppContainerTests {
    @Test("exposes the bundled app info")
    func exposesAppInfo() {
        let container = AppContainer()
        #expect(!container.appInfo.version.isEmpty)
        #expect(!container.appInfo.name.isEmpty)
    }

    @Test("wires an AppStateModel derived from the config repository")
    func wiresAppStateModel() {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        let container = AppContainer(configRepository: repository)
        #expect(container.appStateModel.state == .unconfigured)

        repository.bootstrap(params: TestDefaults.bootstrapParams())
        let containerAfterBootstrap = AppContainer(configRepository: repository)
        #expect(containerAfterBootstrap.appStateModel.state == .configured)
    }

    @Test("logout drops the session and transitions to .configured")
    func logoutTransitionsToConfigured() async {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        repository.bootstrap(params: TestDefaults.bootstrapParams())
        let container = AppContainer(configRepository: repository)

        await container.logout()

        #expect(container.session == nil)
        #expect(container.appStateModel.state == .configured)
    }

    @Test("resetConfiguration wipes config and transitions to .unconfigured")
    func resetConfigurationTransitionsToUnconfigured() async throws {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        repository.bootstrap(params: TestDefaults.bootstrapParams())
        let container = AppContainer(configRepository: repository)
        #expect(container.appStateModel.state == .configured)

        try await container.resetConfiguration()

        #expect(container.session == nil)
        #expect(try repository.available() == false)
        #expect(container.appStateModel.state == .unconfigured)
    }

    @Test("updateUserPassword throws notConfigured when no session is active")
    func updateUserPasswordRequiresSession() async {
        let container = AppContainer()
        await #expect(throws: AppContainer.AppContainerError.notConfigured) {
            try await container.updateUserPassword(currentPassword: "old", newPassword: "new")
        }
    }

    @Test("updateUserSalt throws notConfigured when no session is active")
    func updateUserSaltRequiresSession() async {
        let container = AppContainer()
        await #expect(throws: AppContainer.AppContainerError.notConfigured) {
            try await container.updateUserSalt(currentPassword: "old", newSalt: "salt")
        }
    }

    @Test("importDeviceSecret throws notConfigured when no session is active")
    func importDeviceSecretRequiresSession() async {
        let container = AppContainer()
        await #expect(throws: AppContainer.AppContainerError.notConfigured) {
            try await container.importDeviceSecret(plaintext: Data([0x01]), password: "pw")
        }
    }

    @Test("pushDeviceSecret throws notConfigured when no session is active")
    func pushDeviceSecretRequiresSession() async {
        let container = AppContainer()
        await #expect(throws: AppContainer.AppContainerError.notConfigured) {
            try await container.pushDeviceSecret(password: "pw", remotePassword: nil)
        }
    }

    @Test("pullDeviceSecret throws notConfigured when no session is active")
    func pullDeviceSecretRequiresSession() async {
        let container = AppContainer()
        await #expect(throws: AppContainer.AppContainerError.notConfigured) {
            try await container.pullDeviceSecret(password: "pw", remotePassword: nil)
        }
    }

    @Test("remoteDeviceSecretExists throws notConfigured when no session is active")
    func remoteDeviceSecretExistsRequiresSession() async {
        let container = AppContainer()
        await #expect(throws: AppContainer.AppContainerError.notConfigured) {
            _ = try await container.remoteDeviceSecretExists()
        }
    }
}

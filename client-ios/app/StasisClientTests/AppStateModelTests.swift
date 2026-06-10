@testable import StasisClient
import Testing

@MainActor
@Suite("AppStateModel")
struct AppStateModelTests {
    @Test("init resolves to unconfigured when no config is present")
    func initUnconfigured() {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        let model = AppStateModel(configRepository: repository)
        #expect(model.state == .unconfigured)
    }

    @Test("init resolves to configured when config is present and no restorable session")
    func initConfigured() {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        repository.bootstrap(params: TestDefaults.bootstrapParams())
        let model = AppStateModel(configRepository: repository, restorableSession: false)
        #expect(model.state == .configured)
    }

    @Test("init resolves to restoring when config is present and a restorable session exists")
    func initRestoring() {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        repository.bootstrap(params: TestDefaults.bootstrapParams())
        let model = AppStateModel(configRepository: repository, restorableSession: true)
        #expect(model.state == .restoring)
    }

    @Test("init treats restorable session as irrelevant when no config is present")
    func initIgnoresRestorableWhenUnconfigured() {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        let model = AppStateModel(configRepository: repository, restorableSession: true)
        #expect(model.state == .unconfigured)
    }

    @Test("transition updates the current state")
    func transitionUpdatesState() {
        let preferences = TestDefaults.isolatedDefaults()
        let repository = ConfigRepository(preferences: preferences)
        let model = AppStateModel(configRepository: repository)
        #expect(model.state == .unconfigured)

        model.transition(to: .configured)
        #expect(model.state == .configured)

        model.transition(to: .restoring)
        #expect(model.state == .restoring)

        model.transition(to: .authenticated)
        #expect(model.state == .authenticated)

        model.transition(to: .unconfigured)
        #expect(model.state == .unconfigured)
    }
}

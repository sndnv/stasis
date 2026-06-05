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
}

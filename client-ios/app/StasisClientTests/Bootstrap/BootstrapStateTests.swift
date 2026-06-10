@testable import StasisClient
import Testing

@MainActor
@Suite("BootstrapState")
struct BootstrapStateTests {
    @Test("toRequest prepends https to the host")
    func prependsHttps() {
        let state = BootstrapState()
        state.serverHost = "server.example.com"
        let request = state.toRequest(bootstrapCode: "code")
        #expect(request.serverBootstrapUrl == "https://server.example.com")
    }

    @Test("toRequest carries the bootstrap inputs through")
    func carriesInputs() {
        let state = BootstrapState()
        state.serverHost = "host"
        state.username = "alice"
        state.userPassword = "secret"
        state.overwriteExisting = true
        state.pullSecret = false

        let request = state.toRequest(bootstrapCode: "code-42")

        #expect(request.bootstrapCode == "code-42")
        #expect(request.username == "alice")
        #expect(request.userPassword == "secret")
        #expect(request.overwriteExisting)
        #expect(!request.pullSecret)
        #expect(request.remotePassword == nil)
    }

    @Test("toRequest omits remote password when pull is disabled")
    func omitsRemotePasswordWithoutPull() {
        let state = BootstrapState()
        state.serverHost = "host"
        state.pullSecret = false
        state.overrideRemotePassword = true
        state.remotePassword = "remote"

        let request = state.toRequest(bootstrapCode: "code")

        #expect(request.remotePassword == nil)
    }

    @Test("toRequest omits remote password when override is disabled")
    func omitsRemotePasswordWithoutOverride() {
        let state = BootstrapState()
        state.serverHost = "host"
        state.pullSecret = true
        state.overrideRemotePassword = false
        state.remotePassword = "remote"

        let request = state.toRequest(bootstrapCode: "code")

        #expect(request.remotePassword == nil)
    }

    @Test("toRequest includes remote password when pull + override are on and value is non-empty")
    func includesRemotePassword() {
        let state = BootstrapState()
        state.serverHost = "host"
        state.pullSecret = true
        state.overrideRemotePassword = true
        state.remotePassword = "remote-pw"

        let request = state.toRequest(bootstrapCode: "code")

        #expect(request.remotePassword == "remote-pw")
    }
}

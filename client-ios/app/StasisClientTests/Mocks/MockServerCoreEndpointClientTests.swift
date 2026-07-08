import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("MockServerCoreEndpointClient")
struct MockServerCoreEndpointClientTests {
    private func deviceSecret() throws -> DeviceSecret {
        let defaults = TestDefaults.isolatedDefaults()
        ConfigRepository(preferences: defaults).bootstrap(params: TestDefaults.bootstrapParams())
        return try Secrets.initDeviceSecret(
            user: UUID(),
            device: UUID(),
            secret: Data((0..<32).map { UInt8($0) }),
            preferences: defaults
        )
    }

    @Test("serves fixture crates that decrypt back to the original bytes")
    func roundTripsFixtures() async throws {
        let secret = try deviceSecret()
        let core = MockServerCoreEndpointClient(resolveSecret: { secret }, crates: MockLibraryFixtures.crates)
        let clients = StaticClients(api: StasisClientLibTestSupport.MockServerApiEndpointClient(), core: core)

        for fixture in MockLibraryFixtures.all {
            let content = try #require(fixture.metadata.content)
            let bytes = try await EntityContent.pullBytes(
                metadata: content,
                entityKey: fixture.path,
                deviceSecret: secret,
                clients: clients,
                decryptor: Aes.shared,
                onPartProcessed: {}
            )
            #expect(bytes == fixture.plaintext)
        }
    }

    @Test("returns nil for unknown crates")
    func unknownCrate() async throws {
        let core = MockServerCoreEndpointClient()
        #expect(try await core.pull(crate: UUID()) == nil)
    }
}

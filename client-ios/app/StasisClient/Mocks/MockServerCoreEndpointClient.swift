#if DEBUG
import Foundation
import StasisClientLib

struct MockServerCoreEndpointClient: ServerCoreEndpointClient {
    let selfNode: NodeId = MockConfig.deviceNode
    let server: String = MockConfig.serverCore

    private let resolveSecret: (@Sendable () -> DeviceSecret)?
    private let crates: [CrateId: MockLibraryFixtures.Fixture]

    init() {
        self.resolveSecret = nil
        self.crates = [:]
    }

    init(resolveSecret: @escaping @Sendable () -> DeviceSecret, crates: [CrateId: MockLibraryFixtures.Fixture]) {
        self.resolveSecret = resolveSecret
        self.crates = crates
    }

    func push(manifest: Manifest, content: Data) async throws {}

    func pull(crate: CrateId) async throws -> Data? {
        guard let fixture = crates[crate], let resolveSecret else { return nil }
        let secret = resolveSecret().toFileSecret(forFile: fixture.crateKey, checksum: fixture.checksum)
        return try secret.encrypt(fixture.plaintext)
    }
}
#endif

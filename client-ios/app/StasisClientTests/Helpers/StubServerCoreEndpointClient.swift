import Foundation
import StasisClientLib

final class StubServerCoreEndpointClient: ServerCoreEndpointClient {
    let selfNode: NodeId
    let server: String

    init(selfNode: NodeId = UUID(), server: String = "stub-core") {
        self.selfNode = selfNode
        self.server = server
    }

    func push(manifest: Manifest, content: Data) async throws {
        fatalError("not exercised in these tests")
    }

    func pull(crate: CrateId) async throws -> Data? {
        nil
    }
}

#if DEBUG
import Foundation
import StasisClientLib

struct MockServerCoreEndpointClient: ServerCoreEndpointClient {
    let selfNode: NodeId = MockConfig.deviceNode
    let server: String = MockConfig.serverCore

    func push(manifest: Manifest, content: Data) async throws {}

    func pull(crate: CrateId) async throws -> Data? { nil }
}
#endif

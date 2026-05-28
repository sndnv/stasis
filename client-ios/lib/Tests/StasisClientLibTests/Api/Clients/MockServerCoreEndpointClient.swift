import Foundation
@testable import StasisClientLib

actor MockServerCoreEndpointClient: ServerCoreEndpointClient {
    nonisolated let selfNode: NodeId
    nonisolated let server: String = "mock-core-server"

    private var crates: [CrateId: Data]
    private var pullRequests: [CrateId] = []
    private var pushedManifests: [Manifest] = []

    init(selfNode: NodeId = UUID(), crates: [CrateId: Data] = [:]) {
        self.selfNode = selfNode
        self.crates = crates
    }

    func push(manifest: Manifest, content: Data) async throws {
        pushedManifests.append(manifest)
        crates[manifest.crate] = content
    }

    func pull(crate: CrateId) async throws -> Data? {
        pullRequests.append(crate)
        return crates[crate]
    }

    func recordedCrates() -> [CrateId] { pullRequests }
    func recordedPushes() -> [Manifest] { pushedManifests }
}

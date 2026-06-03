import Foundation
@testable import StasisClientLib

actor MockServerCoreEndpointClient: ServerCoreEndpointClient {
    nonisolated let selfNode: NodeId
    nonisolated let server: String = "mock-core-server"

    private var crates: [CrateId: Data]
    private var pullRequests: [CrateId] = []
    private var pushedManifests: [Manifest] = []
    private let pushDisabled: Bool
    private let pullDisabled: Bool

    init(
        selfNode: NodeId = UUID(),
        crates: [CrateId: Data] = [:],
        pushDisabled: Bool = false,
        pullDisabled: Bool = false
    ) {
        self.selfNode = selfNode
        self.crates = crates
        self.pushDisabled = pushDisabled
        self.pullDisabled = pullDisabled
    }

    func push(manifest: Manifest, content: Data) async throws {
        if pushDisabled {
            throw EndpointFailure(message: "[pushDisabled] is set to [true]")
        }
        pushedManifests.append(manifest)
        crates[manifest.crate] = content
    }

    func pull(crate: CrateId) async throws -> Data? {
        if pullDisabled {
            throw EndpointFailure(message: "[pullDisabled] is set to [true]")
        }
        pullRequests.append(crate)
        return crates[crate]
    }

    func recordedCrates() -> [CrateId] { pullRequests }
    func recordedPushes() -> [Manifest] { pushedManifests }
    func storedCrates() -> [CrateId: Data] { crates }

    var pushCount: Int { pushedManifests.count }
    var pullCount: Int { pullRequests.count }
}

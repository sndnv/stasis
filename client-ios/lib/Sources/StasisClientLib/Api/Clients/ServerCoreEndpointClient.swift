import Foundation

public protocol ServerCoreEndpointClient: ServiceApiClient {
    var selfNode: NodeId { get }
    var server: String { get }

    func push(manifest: Manifest, content: Data) async throws
    func pull(crate: CrateId) async throws -> Data?
}

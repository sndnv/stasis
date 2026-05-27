import Foundation

public struct DefaultServerCoreEndpointClient: ServerCoreEndpointClient, ApiClient {
    public let selfNode: NodeId
    public let server: String
    public let http: HttpClient

    private static let statusOk: Int = 200
    private static let statusNotFound: Int = 404
    private static let statusInsufficientStorage: Int = 507

    public init(
        serverCoreUrl: String,
        credentialsProvider: any CredentialsProvider,
        selfNode: NodeId,
        retryConfig: RetryConfig = .default
    ) {
        self.selfNode = selfNode
        self.server = serverCoreUrl.trimmedTrailingSlash
        self.http = HttpClient(
            credentialsProvider: credentialsProvider,
            retryConfig: retryConfig
        )
    }

    init(serverCoreUrl: String, selfNode: NodeId, http: HttpClient) {
        self.selfNode = selfNode
        self.server = serverCoreUrl.trimmedTrailingSlash
        self.http = http
    }

    public func push(manifest: Manifest, content: Data) async throws {
        let reservation = try await reserveStorage(manifest: manifest)
        try await pushCrate(manifest: manifest, content: content, reservation: reservation)
    }

    public func pull(crate: CrateId) async throws -> Data? {
        let crateId = crate.uuidString.lowercased()
        var request = URLRequest(url: URL(string: "\(server)/crates/\(crateId)")!)
        request.httpMethod = "GET"

        let (data, response) = try await http.send(request)
        switch response.statusCode {
        case Self.statusOk:
            return data
        case Self.statusNotFound:
            return nil
        default:
            throw EndpointFailure(
                message: "Endpoint [\(server)] responded to pull for crate [\(crateId)]"
                    + " with unexpected status: [\(response.statusCode)]"
            )
        }
    }

    private func reserveStorage(manifest: Manifest) async throws -> CrateStorageReservation {
        let storageRequest = CrateStorageRequest(manifest: manifest)

        var request = URLRequest(url: URL(string: "\(server)/reservations")!)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encodeBody(storageRequest)

        let (data, response) = try await http.send(request)
        switch response.statusCode {
        case Self.statusOk:
            return try JSONCoders.decoder().decode(CrateStorageReservation.self, from: data)
        case Self.statusInsufficientStorage:
            throw EndpointFailure(
                message: "Endpoint [\(server)] was unable to reserve enough storage for request [\(storageRequest)]"
            )
        default:
            throw EndpointFailure(
                message: "Endpoint [\(server)] responded to storage request with unexpected status: [\(response.statusCode)]"
            )
        }
    }

    private func pushCrate(
        manifest: Manifest,
        content: Data,
        reservation: CrateStorageReservation
    ) async throws {
        let crateId = manifest.crate.uuidString.lowercased()
        let reservationId = reservation.id.uuidString.lowercased()
        var request = URLRequest(
            url: URL(string: "\(server)/crates/\(crateId)?reservation=\(reservationId)")!
        )
        request.httpMethod = "PUT"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        request.httpBody = content

        let (_, response) = try await http.send(request)
        switch response.statusCode {
        case Self.statusOk:
            return
        default:
            throw EndpointFailure(
                message: "Endpoint [\(server)] responded to push for crate [\(crateId)]"
                    + " with unexpected status: [\(response.statusCode)]"
            )
        }
    }
}

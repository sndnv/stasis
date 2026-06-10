import Foundation
import StasisClientLib

final class MockServerBootstrapEndpointClient: ServerBootstrapEndpointClient, @unchecked Sendable {
    let server: String
    private let outcome: Result<DeviceBootstrapParameters, any Error>

    init(server: String = "https://server.test", outcome: Result<DeviceBootstrapParameters, any Error>) {
        self.server = server
        self.outcome = outcome
    }

    func execute(bootstrapCode: String) async throws -> DeviceBootstrapParameters {
        try outcome.get()
    }
}

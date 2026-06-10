#if DEBUG
import Foundation
import StasisClientLib

struct MockServerBootstrapEndpointClient: ServerBootstrapEndpointClient {
    let server: String = MockConfig.serverApi

    func execute(bootstrapCode: String) async throws -> DeviceBootstrapParameters {
        MockConfig.bootstrapParameters
    }
}
#endif

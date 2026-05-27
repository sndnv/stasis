import Foundation
@testable import StasisClientLib

struct TestApiClient: ApiClient {
    let http: HttpClient
    let transport: HttpTransportStub

    init(credentials: HttpCredentials = .none) {
        let stub = HttpTransportStub()
        self.transport = stub
        self.http = HttpClient(
            transport: stub,
            credentialsProvider: StaticCredentialsProvider(credentials),
            retryConfig: .disabled
        )
    }
}

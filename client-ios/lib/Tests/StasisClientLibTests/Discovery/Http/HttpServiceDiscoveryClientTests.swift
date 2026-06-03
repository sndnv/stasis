import Foundation
@testable import StasisClientLib
import Testing

@Suite("HttpServiceDiscoveryClient")
struct HttpServiceDiscoveryClientTests {
    private struct TestAttributes: ServiceDiscoveryClientAttributes {
        func asServiceDiscoveryRequest(isInitialRequest: Bool) -> ServiceDiscoveryRequest {
            ServiceDiscoveryRequest(isInitialRequest: isInitialRequest, attributes: [:])
        }
    }

    @Test("supports retrieving latest service discovery information")
    func retrievesLatest() async throws {
        let stub = HttpTransportStub()
        await stub.enqueue(.init(statusCode: 200, body: Data(#"{"result":"keep-existing"}"#.utf8)))

        let http = HttpClient(
            transport: stub,
            credentialsProvider: StaticHttpCredentialsProvider(
                .basic(username: "some-user", password: "some-password")
            ),
            retryConfig: .disabled
        )

        let client = HttpServiceDiscoveryClient(
            apiUrl: "http://localhost/",
            attributes: TestAttributes(),
            http: http
        )

        let result = try await client.latest(isInitialRequest: false)
        #expect(result == .keepExisting)

        let requests = await stub.recordedRequests()
        let request = try #require(requests.first)
        #expect(request.url?.absoluteString == "http://localhost/v1/discovery/provide")
        #expect(request.httpMethod == "POST")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Basic c29tZS11c2VyOnNvbWUtcGFzc3dvcmQ=")

        let body = try #require(request.httpBody)
        let decoded = try JSONCoders.decoder().decode(ServiceDiscoveryRequest.self, from: body)
        #expect(decoded == ServiceDiscoveryRequest(isInitialRequest: false, attributes: [:]))
    }

    @Test("trims trailing slashes from the api url")
    func trimsTrailingSlashes() {
        let client = HttpServiceDiscoveryClient(
            apiUrl: "http://localhost/",
            credentialsProvider: StaticHttpCredentialsProvider(.none),
            attributes: TestAttributes()
        )

        #expect(client.server == "http://localhost")
    }
}

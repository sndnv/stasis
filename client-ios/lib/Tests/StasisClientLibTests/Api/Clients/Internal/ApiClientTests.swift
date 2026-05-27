import Foundation
@testable import StasisClientLib
import Testing

@Suite("ApiClient")
struct ApiClientTests {
    private let url = URL(string: "http://localhost/test")!

    @Test("encodes model classes as JSON")
    func encodesBody() throws {
        let client = TestApiClient()
        let value = TestDataClass(int: 42, bool: true, string: "test")

        let encoded = try client.encodeBody(value)
        let decoded = try JSONCoders.decoder().decode(TestDataClass.self, from: encoded)
        #expect(decoded == value)
    }

    @Test("decodes response data into a model")
    func decodesModel() async throws {
        let client = TestApiClient()
        let expected = TestDataClass(int: 1, bool: false, string: "other")
        await client.transport.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let result: TestDataClass = try await client.jsonRequest(URLRequest(url: url))
        #expect(result == expected)
    }

    @Test("decodes response data into a list of models")
    func decodesList() async throws {
        let client = TestApiClient()
        let expected = [
            TestDataClass(int: 1, bool: false, string: "a"),
            TestDataClass(int: 2, bool: true, string: "b"),
            TestDataClass(int: 3, bool: false, string: "c")
        ]
        await client.transport.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let result: [TestDataClass] = try await client.jsonListRequest(URLRequest(url: url))
        #expect(result == expected)
    }

    @Test("supports requests without a response body")
    func emptyRequest() async throws {
        let client = TestApiClient()
        await client.transport.enqueue(.init(statusCode: 204))

        try await client.emptyRequest(URLRequest(url: url))

        let recorded = await client.transport.recordedRequests()
        #expect(recorded.count == 1)
    }

    @Test("fails to decode an empty response into a required model")
    func failsOnEmptyResponse() async {
        let client = TestApiClient()
        await client.transport.enqueue(.init(statusCode: 200, body: Data()))

        await #expect(throws: DecodingError.self) {
            let _: TestDataClass = try await client.jsonRequest(URLRequest(url: url))
        }
    }

    @Test("sends requests with credentials")
    func sendsWithCredentials() async throws {
        let client = TestApiClient(credentials: .oauth2BearerToken(token: "test-token"))
        await client.transport.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(
            TestDataClass(int: 0, bool: false, string: "")
        )))

        let _: TestDataClass = try await client.jsonRequest(URLRequest(url: url))

        let recorded = await client.transport.recordedRequests()
        #expect(recorded.first?.value(forHTTPHeaderField: HttpCredentials.authorizationHeader)
            == "Bearer test-token")
    }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("HttpClient")
struct HttpClientTests {
    private func makeClient(
        credentials: HttpCredentials = .none,
        retryConfig: RetryConfig = .disabled
    ) -> (HttpClient, HttpTransportStub) {
        let stub = HttpTransportStub()
        let client = HttpClient(
            transport: stub,
            credentialsProvider: StaticCredentialsProvider(credentials),
            retryConfig: retryConfig
        )
        return (client, stub)
    }

    @Test("supports making requests with credentials")
    func sendsWithCredentials() async throws {
        let (client, stub) = makeClient(credentials: .oauth2BearerToken(token: "test-token"))
        await stub.enqueue(.init(statusCode: 200, body: Data("test-response".utf8)))

        let request = URLRequest(url: URL(string: "http://localhost/test")!)
        let (data, response) = try await client.send(request)

        #expect(String(data: data, encoding: .utf8) == "test-response")
        #expect(response.statusCode == 200)

        let recorded = await stub.recordedRequests()
        #expect(recorded.count == 1)
        #expect(recorded.first?.value(forHTTPHeaderField: HttpCredentials.authorizationHeader)
            == "Bearer test-token")
    }

    @Test("preserves pre-set Authorization header when provider is none")
    func preservesPresetAuthorizationHeader() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200))

        var request = URLRequest(url: URL(string: "http://localhost/test")!)
        request.setValue("Bearer preset", forHTTPHeaderField: HttpCredentials.authorizationHeader)
        _ = try await client.send(request)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.value(forHTTPHeaderField: HttpCredentials.authorizationHeader)
            == "Bearer preset")
    }

    @Test("returns raw response without throwing on non-2xx")
    func returnsRawResponse() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 500))

        let (_, response) = try await client.send(URLRequest(url: URL(string: "http://localhost/test")!))
        #expect(response.statusCode == 500)
    }

    @Test("retries retryable responses up to maxRetries")
    func retriesRetryable() async throws {
        let (client, stub) = makeClient(retryConfig: .init(
            minBackoff: .milliseconds(1),
            maxBackoff: .milliseconds(2),
            randomFactor: 0.1,
            maxRetries: 3
        ))
        await stub.enqueue(.init(statusCode: 503))
        await stub.enqueue(.init(statusCode: 503))
        await stub.enqueue(.init(statusCode: 503))
        await stub.enqueue(.init(statusCode: 200, body: Data("ok".utf8)))

        let (data, response) = try await client.send(URLRequest(url: URL(string: "http://localhost/test")!))
        #expect(response.statusCode == 200)
        #expect(String(data: data, encoding: .utf8) == "ok")

        let recorded = await stub.recordedRequests()
        #expect(recorded.count == 4)
    }

    @Test("does not retry non-retryable responses")
    func doesNotRetryNonRetryable() async throws {
        let (client, stub) = makeClient(retryConfig: .init(
            minBackoff: .milliseconds(1),
            maxBackoff: .milliseconds(2),
            randomFactor: 0.1,
            maxRetries: 5
        ))
        await stub.enqueue(.init(statusCode: 404))

        let (_, response) = try await client.send(URLRequest(url: URL(string: "http://localhost/test")!))
        #expect(response.statusCode == 404)

        let recorded = await stub.recordedRequests()
        #expect(recorded.count == 1)
    }

    @Test("stops retrying after maxRetries even if still retryable")
    func stopsAfterMaxRetries() async throws {
        let (client, stub) = makeClient(retryConfig: .init(
            minBackoff: .milliseconds(1),
            maxBackoff: .milliseconds(2),
            randomFactor: 0.1,
            maxRetries: 2
        ))
        await stub.enqueue(.init(statusCode: 503))
        await stub.enqueue(.init(statusCode: 503))
        await stub.enqueue(.init(statusCode: 503))

        let (_, response) = try await client.send(URLRequest(url: URL(string: "http://localhost/test")!))
        #expect(response.statusCode == 503)

        let recorded = await stub.recordedRequests()
        #expect(recorded.count == 3)
    }
}

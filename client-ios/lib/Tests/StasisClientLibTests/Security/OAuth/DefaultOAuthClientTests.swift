import Foundation
@testable import StasisClientLib
import Testing

@Suite("DefaultOAuthClient")
struct DefaultOAuthClientTests {
    private let endpoint = "http://localhost/oauth/token"
    private let clientCredentials = HttpCredentials.basic(
        username: "test-client", password: "test-client-password"
    )
    private let expectedResponse = AccessTokenResponse(
        accessToken: "test-token",
        refreshToken: "test-refresh-token",
        expiresIn: 42,
        scope: "test-scope"
    )

    @Test("successfully retrieves tokens (client credentials)")
    func clientCredentialsGrant() async throws {
        let transport = HttpTransportStub()
        await transport.enqueue(.init(statusCode: 200, body: try encode(expectedResponse)))

        let client = try DefaultOAuthClient(
            tokenEndpoint: endpoint,
            client: "test-client",
            clientSecret: "test-client-password",
            transport: transport
        )

        let result = await client.token(scope: "test-scope", parameters: .clientCredentials)
        #expect(try result.get() == expectedResponse)

        let recorded = await transport.recordedRequests()
        try assertRequest(
            recorded.first,
            body: "scope=test-scope&grant_type=client_credentials"
        )
    }

    @Test("successfully retrieves tokens (resource owner password credentials)")
    func passwordGrant() async throws {
        let transport = HttpTransportStub()
        await transport.enqueue(.init(statusCode: 200, body: try encode(expectedResponse)))

        let client = try DefaultOAuthClient(
            tokenEndpoint: endpoint,
            client: "test-client",
            clientSecret: "test-client-password",
            transport: transport
        )

        let result = await client.token(
            scope: "test-scope",
            parameters: .resourceOwnerPasswordCredentials(
                username: "test-user", password: "test-user-password"
            )
        )
        #expect(try result.get() == expectedResponse)

        let recorded = await transport.recordedRequests()
        try assertRequest(
            recorded.first,
            body: "scope=test-scope&grant_type=password&username=test-user&password=test-user-password"
        )
    }

    @Test("successfully retrieves tokens (refresh)")
    func refreshGrant() async throws {
        let transport = HttpTransportStub()
        await transport.enqueue(.init(statusCode: 200, body: try encode(expectedResponse)))

        let client = try DefaultOAuthClient(
            tokenEndpoint: endpoint,
            client: "test-client",
            clientSecret: "test-client-password",
            transport: transport
        )

        let result = await client.token(
            scope: "test-scope",
            parameters: .refreshToken("test-token")
        )
        #expect(try result.get() == expectedResponse)

        let recorded = await transport.recordedRequests()
        try assertRequest(
            recorded.first,
            body: "scope=test-scope&grant_type=refresh_token&refresh_token=test-token"
        )
    }

    @Test("supports providing no scope")
    func supportsNoScope() async throws {
        let transport = HttpTransportStub()
        await transport.enqueue(.init(statusCode: 200, body: try encode(expectedResponse)))

        let client = try DefaultOAuthClient(
            tokenEndpoint: endpoint,
            client: "test-client",
            clientSecret: "test-client-password",
            transport: transport
        )

        let result = await client.token(scope: nil, parameters: .clientCredentials)
        #expect(try result.get() == expectedResponse)

        let recorded = await transport.recordedRequests()
        try assertRequest(recorded.first, body: "grant_type=client_credentials")
    }

    @Test("supports handling unauthorized responses")
    func handlesUnauthorized() async throws {
        let transport = HttpTransportStub()
        await transport.enqueue(.init(statusCode: DefaultOAuthClient.statusUnauthorized))

        let client = try DefaultOAuthClient(
            tokenEndpoint: endpoint,
            client: "test-client",
            clientSecret: "test-client-password",
            transport: transport
        )

        let result = await client.token(scope: nil, parameters: .clientCredentials)
        #expect(throws: AccessDeniedFailure.self) { try result.get() }

        let recorded = await transport.recordedRequests()
        try assertRequest(recorded.first, body: "grant_type=client_credentials")
    }

    @Test("supports handling failed responses")
    func handlesFailedResponse() async throws {
        let transport = HttpTransportStub()
        await transport.enqueue(.init(statusCode: 500))

        let client = try DefaultOAuthClient(
            tokenEndpoint: endpoint,
            client: "test-client",
            clientSecret: "test-client-password",
            transport: transport
        )

        let result = await client.token(scope: nil, parameters: .clientCredentials)
        guard case .failure(let error) = result, let endpointError = error as? EndpointFailure else {
            Issue.record("expected EndpointFailure, got \(result)")
            return
        }
        #expect(endpointError.message.contains("500"))

        let recorded = await transport.recordedRequests()
        try assertRequest(recorded.first, body: "grant_type=client_credentials")
    }

    private func encode(_ response: AccessTokenResponse) throws -> Data {
        try JSONEncoder().encode(response)
    }

    private func assertRequest(_ request: URLRequest?, body expectedBody: String) throws {
        let request = try #require(request)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.absoluteString == endpoint)
        let body = try #require(request.httpBody.flatMap { String(data: $0, encoding: .utf8) })
        #expect(body == expectedBody)
        let auth = try #require(request.value(forHTTPHeaderField: HttpCredentials.authorizationHeader))
        let expectedAuth = "Basic " + Data("test-client:test-client-password".utf8).base64EncodedString()
        #expect(auth == expectedAuth)
        let contentType = try #require(request.value(forHTTPHeaderField: "Content-Type"))
        #expect(contentType == "application/x-www-form-urlencoded")
    }
}

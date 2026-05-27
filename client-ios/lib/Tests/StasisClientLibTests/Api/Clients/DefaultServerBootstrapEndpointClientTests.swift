import Foundation
@testable import StasisClientLib
import Testing

@Suite("DefaultServerBootstrapEndpointClient")
struct DefaultServerBootstrapEndpointClientTests {
    private let server = "http://localhost:1234"
    private let testCode = "test-code"

    private func makeClient() -> (DefaultServerBootstrapEndpointClient, HttpTransportStub) {
        let stub = HttpTransportStub()
        let http = HttpClient(
            transport: stub,
            credentialsProvider: StaticCredentialsProvider(.none),
            retryConfig: .disabled
        )
        return (DefaultServerBootstrapEndpointClient(serverBootstrapUrl: server, http: http), stub)
    }

    private static let testParams = DeviceBootstrapParameters(
        authentication: .init(
            tokenEndpoint: "http://localhost:1234",
            clientId: UUID().uuidString,
            clientSecret: "test-secret",
            scopes: .init(
                api: "urn:stasis:identity:audience:server-api",
                core: "urn:stasis:identity:audience:\(UUID().uuidString)"
            )
        ),
        serverApi: .init(
            url: "http://localhost:5678",
            user: UUID().uuidString,
            userSalt: "test-salt",
            device: UUID().uuidString
        ),
        serverCore: .init(
            address: "http://localhost:5679",
            nodeId: "test-node"
        ),
        secrets: .init(
            derivation: .init(
                encryption: .init(secretSize: 16, iterations: 100000, saltPrefix: "test-prefix"),
                authentication: .init(enabled: true, secretSize: 16, iterations: 100000, saltPrefix: "test-prefix")
            ),
            encryption: .init(
                file: .init(keySize: 16, ivSize: 12),
                metadata: .init(keySize: 16, ivSize: 12),
                deviceSecret: .init(keySize: 16, ivSize: 12)
            )
        )
    )

    @Test("executes device bootstrap")
    func executesBootstrap() async throws {
        let (client, stub) = makeClient()
        let expected = Self.testParams
        let body = try JSONCoders.encoder().encode(expected)
        await stub.enqueue(.init(statusCode: 200, body: body))

        let actual = try await client.execute(bootstrapCode: testCode)
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.count == 1)
        let request = try #require(recorded.first)
        #expect(request.url?.path == "/v1/devices/execute")
        #expect(request.httpMethod == "PUT")
        #expect((request.httpBody ?? Data()).isEmpty)
        #expect(request.value(forHTTPHeaderField: HttpCredentials.authorizationHeader)
            == "Bearer \(testCode)")
    }

    @Test("handles bootstrap request failures (unauthorized)")
    func handlesUnauthorized() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 401))

        await #expect(throws: InvalidBootstrapCodeFailure.self) {
            try await client.execute(bootstrapCode: testCode)
        }
    }

    @Test("handles bootstrap request failures (server failures)")
    func handlesServerFailures() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 505))

        do {
            _ = try await client.execute(bootstrapCode: testCode)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message.contains("responded with [505"))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("trims trailing slash from server URL")
    func trimsTrailingSlash() {
        let client = DefaultServerBootstrapEndpointClient(serverBootstrapUrl: "http://localhost:1234/")
        #expect(client.server == "http://localhost:1234")
    }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("DefaultServerCoreEndpointClient")
struct DefaultServerCoreEndpointClientTests {
    private let server = "http://localhost:1234"
    private let coreCredentials = HttpCredentials.basic(username: "some-user", password: "some-password")

    private func makeClient() -> (DefaultServerCoreEndpointClient, HttpTransportStub) {
        let stub = HttpTransportStub()
        let http = HttpClient(
            transport: stub,
            credentialsProvider: StaticHttpCredentialsProvider(coreCredentials),
            retryConfig: .disabled
        )
        return (
            DefaultServerCoreEndpointClient(serverCoreUrl: server, selfNode: UUID(), http: http),
            stub
        )
    }

    private let crateId = UUID()
    private let crateContent = Data("some-crate".utf8)

    private var manifest: Manifest {
        Manifest(
            crate: crateId,
            size: Int64(crateContent.count),
            copies: 1,
            origin: UUID(),
            source: UUID()
        )
    }

    @Test("pushes crates")
    func pushesCrates() async throws {
        let (client, stub) = makeClient()
        let manifest = self.manifest
        let reservation = CrateStorageReservation(
            id: UUID(),
            crate: manifest.crate,
            size: manifest.size,
            copies: manifest.copies,
            origin: manifest.origin,
            target: UUID()
        )
        let reservationBody = try JSONCoders.encoder().encode(reservation)
        await stub.enqueue(.init(statusCode: 200, body: reservationBody))
        await stub.enqueue(.init(statusCode: 200))

        try await client.push(manifest: manifest, content: crateContent)

        let recorded = await stub.recordedRequests()
        #expect(recorded.count == 2)

        let reservationRequest = recorded[0]
        #expect(reservationRequest.httpMethod == "PUT")
        #expect(reservationRequest.url?.path == "/reservations")
        let decoded = try JSONCoders.decoder().decode(
            CrateStorageRequest.self,
            from: reservationRequest.httpBody ?? Data()
        )
        #expect(decoded.crate == manifest.crate)
        #expect(decoded.size == manifest.size)
        #expect(decoded.copies == manifest.copies)
        #expect(decoded.origin == manifest.origin)
        #expect(decoded.source == manifest.source)

        let pushRequest = recorded[1]
        #expect(pushRequest.httpMethod == "PUT")
        #expect(pushRequest.url?.path == "/crates/\(manifest.crate.uuidString.lowercased())")
        #expect(pushRequest.url?.query == "reservation=\(reservation.id.uuidString.lowercased())")
        #expect(pushRequest.httpBody == crateContent)
    }

    @Test("fails to push crates if storage is not available")
    func failsOnInsufficientStorage() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 507))

        do {
            try await client.push(manifest: manifest, content: crateContent)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message.contains("was unable to reserve enough storage for request"))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("handles reservation failures")
    func handlesReservationFailures() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 505))

        do {
            try await client.push(manifest: manifest, content: crateContent)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message.contains("responded to storage request with unexpected status: [505]"))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("handles push failures")
    func handlesPushFailures() async throws {
        let (client, stub) = makeClient()
        let manifest = self.manifest
        let reservation = CrateStorageReservation(
            id: UUID(),
            crate: manifest.crate,
            size: manifest.size,
            copies: manifest.copies,
            origin: manifest.origin,
            target: UUID()
        )
        let reservationBody = try JSONCoders.encoder().encode(reservation)
        await stub.enqueue(.init(statusCode: 200, body: reservationBody))
        await stub.enqueue(.init(statusCode: 505))

        do {
            try await client.push(manifest: manifest, content: crateContent)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message.contains(
                "responded to push for crate [\(manifest.crate.uuidString.lowercased())] with unexpected status: [505]"
            ))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("pulls crates")
    func pullsCrates() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200, body: crateContent))

        let actual = try await client.pull(crate: crateId)
        #expect(actual == crateContent)

        let recorded = await stub.recordedRequests()
        #expect(recorded.count == 1)
        #expect(recorded.first?.httpMethod == "GET")
        #expect(recorded.first?.url?.path == "/crates/\(crateId.uuidString.lowercased())")
    }

    @Test("returns nil when pulling missing crates")
    func returnsNilOnMissing() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 404))

        let actual = try await client.pull(crate: crateId)
        #expect(actual == nil)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.httpMethod == "GET")
        #expect(recorded.first?.url?.path == "/crates/\(crateId.uuidString.lowercased())")
    }

    @Test("handles pull failures")
    func handlesPullFailures() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 505))

        do {
            _ = try await client.pull(crate: crateId)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message.contains(
                "responded to pull for crate [\(crateId.uuidString.lowercased())] with unexpected status: [505]"
            ))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }
}

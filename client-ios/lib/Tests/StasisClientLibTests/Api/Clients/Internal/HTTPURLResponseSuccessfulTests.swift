import Foundation
@testable import StasisClientLib
import Testing

@Suite("HTTPURLResponse.successful")
struct HTTPURLResponseSuccessfulTests {
    private let url = URL(string: "http://localhost/test")!

    private func response(_ status: Int) -> HTTPURLResponse {
        HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
    }

    @Test("passes through 2xx")
    func passesThrough2xx() throws {
        try response(200).successful()
        try response(204).successful()
        try response(299).successful()
    }

    @Test("maps 401 to AccessDeniedFailure")
    func mapsUnauthorized() {
        #expect(throws: AccessDeniedFailure.self) {
            try response(401).successful()
        }
    }

    @Test("maps 403 to AccessDeniedFailure")
    func mapsForbidden() {
        #expect(throws: AccessDeniedFailure.self) {
            try response(403).successful()
        }
    }

    @Test("maps 404 to ResourceMissingFailure")
    func mapsNotFound() {
        #expect(throws: ResourceMissingFailure.self) {
            try response(404).successful()
        }
    }

    @Test("maps other non-2xx to EndpointFailure with status code in message")
    func mapsOtherNon2xx() throws {
        do {
            try response(500).successful()
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message.contains("500"))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }
}

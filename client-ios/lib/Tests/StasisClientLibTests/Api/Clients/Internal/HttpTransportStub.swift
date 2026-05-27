import Foundation
@testable import StasisClientLib

public actor HttpTransportStub: HttpTransport {
    public struct Stub: Sendable {
        public let statusCode: Int
        public let body: Data
        public let headers: [String: String]

        public init(statusCode: Int = 200, body: Data = Data(), headers: [String: String] = [:]) {
            self.statusCode = statusCode
            self.body = body
            self.headers = headers
        }
    }

    private var stubs: [Stub] = []
    private var requests: [URLRequest] = []

    public init() {}

    public func enqueue(_ stub: Stub) {
        stubs.append(stub)
    }

    public func recordedRequests() -> [URLRequest] {
        requests
    }

    public func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        let stub = stubs.isEmpty ? Stub(statusCode: 500) : stubs.removeFirst()
        let url = request.url ?? URL(string: "http://localhost")!
        let response = HTTPURLResponse(
            url: url,
            statusCode: stub.statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: stub.headers
        )!
        return (stub.body, response)
    }
}

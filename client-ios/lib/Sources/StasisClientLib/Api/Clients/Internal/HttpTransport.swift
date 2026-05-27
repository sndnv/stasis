import Foundation

public protocol HttpTransport: Sendable {
    func data(for request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: HttpTransport {}

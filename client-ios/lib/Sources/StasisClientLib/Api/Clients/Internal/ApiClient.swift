import Foundation

public protocol ApiClient: Sendable {
    var http: HttpClient { get }
}

extension ApiClient {
    public func jsonRequest<T: Decodable & Sendable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await http.send(request)
        try response.successful()
        return try JSONCoders.decoder().decode(T.self, from: data)
    }

    public func jsonListRequest<T: Decodable & Sendable>(_ request: URLRequest) async throws -> [T] {
        let (data, response) = try await http.send(request)
        try response.successful()
        return try JSONCoders.decoder().decode([T].self, from: data)
    }

    public func emptyRequest(_ request: URLRequest) async throws {
        let (_, response) = try await http.send(request)
        try response.successful()
    }

    public func encodeBody<T: Encodable>(_ value: T) throws -> Data {
        try JSONCoders.encoder().encode(value)
    }
}

import Foundation

public struct HttpClient: Sendable {
    private let transport: any HttpTransport
    private let credentialsProvider: any HttpCredentialsProvider
    private let retryConfig: RetryConfig

    public init(
        transport: any HttpTransport = URLSession.shared,
        credentialsProvider: any HttpCredentialsProvider,
        retryConfig: RetryConfig = .default
    ) {
        self.transport = transport
        self.credentialsProvider = credentialsProvider
        self.retryConfig = retryConfig
    }

    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let credentials = await credentialsProvider.credentials()
        let authorized = request.withCredentials(credentials)
        return try await Retry.send(config: retryConfig) {
            let (data, response) = try await transport.data(for: authorized)
            guard let http = response as? HTTPURLResponse else {
                throw EndpointFailure(message: "Server responded with a non-HTTP response")
            }
            return (data, http)
        }
    }
}

extension HTTPURLResponse {
    public static let statusUnauthorized: Int = 401
    public static let statusForbidden: Int = 403
    public static let statusNotFound: Int = 404

    public func successful() throws {
        switch statusCode {
        case 200..<300:
            return
        case Self.statusUnauthorized, Self.statusForbidden:
            throw AccessDeniedFailure()
        case Self.statusNotFound:
            throw ResourceMissingFailure()
        default:
            let message = HTTPURLResponse.localizedString(forStatusCode: statusCode)
            throw EndpointFailure(message: "Server responded with [\(statusCode) - \(message)]")
        }
    }
}

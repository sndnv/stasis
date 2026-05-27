import Foundation

public struct HttpServiceDiscoveryClient: ServiceDiscoveryClient, ApiClient {
    public let server: String
    public let attributes: any ServiceDiscoveryClientAttributes
    public let http: HttpClient

    public init(
        apiUrl: String,
        credentialsProvider: any CredentialsProvider,
        attributes: any ServiceDiscoveryClientAttributes,
        retryConfig: RetryConfig = .default
    ) {
        self.server = apiUrl.trimmedTrailingSlash
        self.attributes = attributes
        self.http = HttpClient(
            credentialsProvider: credentialsProvider,
            retryConfig: retryConfig
        )
    }

    init(apiUrl: String, attributes: any ServiceDiscoveryClientAttributes, http: HttpClient) {
        self.server = apiUrl.trimmedTrailingSlash
        self.attributes = attributes
        self.http = http
    }

    public func latest(isInitialRequest: Bool) async throws -> ServiceDiscoveryResult {
        var request = URLRequest(url: URL(string: "\(server)/v1/discovery/provide")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encodeBody(attributes.asServiceDiscoveryRequest(isInitialRequest: isInitialRequest))
        return try await jsonRequest(request)
    }
}

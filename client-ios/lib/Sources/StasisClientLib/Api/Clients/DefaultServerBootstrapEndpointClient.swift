import Foundation

public struct DefaultServerBootstrapEndpointClient: ServerBootstrapEndpointClient {
    public let server: String
    public let http: HttpClient

    public init(serverBootstrapUrl: String, retryConfig: RetryConfig = .default) {
        self.server = serverBootstrapUrl.trimmedTrailingSlash
        self.http = HttpClient(
            credentialsProvider: StaticHttpCredentialsProvider(.none),
            retryConfig: retryConfig
        )
    }

    init(serverBootstrapUrl: String, http: HttpClient) {
        self.server = serverBootstrapUrl.trimmedTrailingSlash
        self.http = http
    }

    public func execute(bootstrapCode: String) async throws -> DeviceBootstrapParameters {
        var request = URLRequest(url: URL(string: "\(server)/v1/devices/execute")!)
        request.httpMethod = "PUT"
        request.httpBody = Data()
        request.setCredentials(.oauth2BearerToken(token: bootstrapCode))

        let (data, response) = try await http.send(request)

        switch response.statusCode {
        case 200..<300:
            return try JSONCoders.decoder().decode(DeviceBootstrapParameters.self, from: data)
        case HTTPURLResponse.statusUnauthorized:
            throw InvalidBootstrapCodeFailure()
        default:
            let message = HTTPURLResponse.localizedString(forStatusCode: response.statusCode)
            throw EndpointFailure(
                message: "Server [\(server)] responded with [\(response.statusCode) - \(message)]"
            )
        }
    }
}

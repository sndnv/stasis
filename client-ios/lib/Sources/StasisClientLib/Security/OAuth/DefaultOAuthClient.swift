import Foundation

public struct DefaultOAuthClient: OAuthClient {
    public static let statusUnauthorized: Int = 401

    private let tokenEndpoint: URL
    private let credentials: HttpCredentials
    private let transport: any HttpTransport

    public init(
        tokenEndpoint: String,
        client: String,
        clientSecret: String,
        transport: any HttpTransport = URLSession.shared
    ) throws {
        guard let url = URL(string: tokenEndpoint) else {
            throw EndpointFailure(message: "Invalid token endpoint URL [\(tokenEndpoint)]")
        }
        self.tokenEndpoint = url
        self.credentials = .basic(username: client, password: clientSecret)
        self.transport = transport
    }

    public func token(
        scope: String?,
        parameters: GrantParameters
    ) async -> Result<AccessTokenResponse, Error> {
        var items: [URLQueryItem] = []
        if let scope {
            items.append(URLQueryItem(name: "scope", value: scope))
        }
        switch parameters {
        case .clientCredentials:
            items.append(URLQueryItem(name: "grant_type", value: GrantType.clientCredentials))
        case let .resourceOwnerPasswordCredentials(username, password):
            items.append(URLQueryItem(name: "grant_type", value: GrantType.resourceOwnerPasswordCredentials))
            items.append(URLQueryItem(name: "username", value: username))
            items.append(URLQueryItem(name: "password", value: password))
        case let .refreshToken(token):
            items.append(URLQueryItem(name: "grant_type", value: GrantType.refreshToken))
            items.append(URLQueryItem(name: "refresh_token", value: token))
        }

        var components = URLComponents()
        components.queryItems = items

        do {
            var request = URLRequest(url: tokenEndpoint)
            request.httpMethod = "POST"
            request.setValue(
                "application/x-www-form-urlencoded",
                forHTTPHeaderField: "Content-Type"
            )
            request.httpBody = Data((components.percentEncodedQuery ?? "").utf8)
            let authorized = request.withCredentials(credentials)

            let (data, response) = try await transport.data(for: authorized)
            guard let http = response as? HTTPURLResponse else {
                return .failure(EndpointFailure(message: "Server responded with a non-HTTP response"))
            }
            switch http.statusCode {
            case 200..<300:
                let decoded = try JSONDecoder().decode(AccessTokenResponse.self, from: data)
                return .success(decoded)
            case Self.statusUnauthorized:
                return .failure(AccessDeniedFailure())
            default:
                let message = HTTPURLResponse.localizedString(forStatusCode: http.statusCode)
                return .failure(EndpointFailure(
                    message: "Server responded with [\(http.statusCode) - \(message)]"
                ))
            }
        } catch {
            return .failure(error)
        }
    }
}

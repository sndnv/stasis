import Foundation

public protocol OAuthClient: Sendable {
    func token(scope: String?, parameters: GrantParameters) async -> Result<AccessTokenResponse, Error>
}

public enum GrantType {
    public static let clientCredentials: String = "client_credentials"
    public static let resourceOwnerPasswordCredentials: String = "password"
    public static let refreshToken: String = "refresh_token"
}

public enum GrantParameters: Sendable, Equatable {
    case clientCredentials
    case resourceOwnerPasswordCredentials(username: String, password: String)
    case refreshToken(String)
}

#if DEBUG
import Foundation
import StasisClientLib

struct MockOAuthClient: OAuthClient {
    static let tokenValidity: TimeInterval = 3600

    func token(scope: String?, parameters: GrantParameters) async -> Result<AccessTokenResponse, Error> {
        .success(
            AccessTokenResponse(
                accessToken: Self.makeAccessToken(scope: scope),
                refreshToken: "mock-refresh-token",
                expiresIn: Int64(Self.tokenValidity),
                scope: scope
            )
        )
    }

    private static func makeAccessToken(scope: String?) -> String {
        let issuedAt = Date()
        let expiresAt = issuedAt.addingTimeInterval(tokenValidity)
        let header = encode(["alg": "none", "typ": "JWT"])
        let payload = encode([
            "sub": MockConfig.user.uuidString,
            "scope": scope ?? "",
            "iat": Int(issuedAt.timeIntervalSince1970),
            "exp": Int(expiresAt.timeIntervalSince1970)
        ])
        return "\(header).\(payload).mock-signature"
    }

    private static func encode(_ claims: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: claims) else {
            return ""
        }
        return data.base64UrlEncodedString()
    }
}
#endif

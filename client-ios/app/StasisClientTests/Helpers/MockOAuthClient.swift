import Foundation
import StasisClientLib

final class MockOAuthClient: OAuthClient, @unchecked Sendable {
    private let coreOutcome: Result<AccessTokenResponse, any Error>
    private let apiOutcome: Result<AccessTokenResponse, any Error>

    init(
        coreOutcome: Result<AccessTokenResponse, any Error>,
        apiOutcome: Result<AccessTokenResponse, any Error>
    ) {
        self.coreOutcome = coreOutcome
        self.apiOutcome = apiOutcome
    }

    func token(scope: String?, parameters: GrantParameters) async -> Result<AccessTokenResponse, any Error> {
        switch parameters {
        case .clientCredentials:
            return coreOutcome
        case .resourceOwnerPasswordCredentials, .refreshToken:
            return apiOutcome
        }
    }
}

extension AccessTokenResponse {
    static func test(
        accessToken: String = "token",
        refreshToken: String? = "refresh",
        expiresIn: Int64 = 3600,
        scope: String? = nil
    ) -> AccessTokenResponse {
        AccessTokenResponse(
            accessToken: accessToken,
            refreshToken: refreshToken,
            expiresIn: expiresIn,
            scope: scope
        )
    }
}

package stasis.client_android.mocks

import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.utils.Try

class MockOAuthClient : OAuthClient {
    override suspend fun token(
        scope: String?,
        parameters: OAuthClient.GrantParameters
    ): Try<AccessTokenResponse> = Try.Success(
        AccessTokenResponse(
            access_token = Generators.generateJwt(sub = "test-subject"),
            refresh_token = null,
            expires_in = 42,
            scope = null
        )
    )
}

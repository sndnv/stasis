package stasis.identity.model.errors

sealed abstract class TokenError(
  val error: String,
  val error_description: String
)

object TokenError {
  final case object InvalidRequest
      extends TokenError(
        error = "invalid_request",
        error_description = s"""
           |The request is missing a required parameter, includes an
           |unsupported parameter value (other than grant type),
           |repeats a parameter, includes multiple credentials,
           |utilizes more than one mechanism for authenticating the
           |client, or is otherwise malformed.
           """.stripMargin.replaceAll("\n", " ").trim
      )

  final case object InvalidClient
      extends TokenError(
        error = "invalid_client",
        error_description = s"""
           |Client authentication failed (e.g., unknown client, no
           |client authentication included, or unsupported
           |authentication method).  The authorization server MAY
           |return an HTTP 401 (Unauthorized) status code to indicate
           |which HTTP authentication schemes are supported.  If the
           |client attempted to authenticate via the "Authorization"
           |request header field, the authorization server MUST
           |respond with an HTTP 401 (Unauthorized) status code and
           |include the "WWW-Authenticate" response header field
           |matching the authentication scheme used by the client.
           """.stripMargin.replaceAll("\n", " ").trim
      )

  final case object InvalidGrant
      extends TokenError(
        error = "invalid_grant",
        error_description = s"""
           |The provided authorization grant (e.g., authorization
           |code, resource owner credentials) or refresh token is
           |invalid, expired, revoked, does not match the redirection
           |URI used in the authorization request, or was issued to
           |another client.
           """.stripMargin.replaceAll("\n", " ").trim
      )

  final case object UnauthorizedClient
      extends TokenError(
        error = "unauthorized_client",
        error_description = s"""
           |The authenticated client is not authorized to use this
           |authorization grant type.
           """.stripMargin.replaceAll("\n", " ").trim
      )

  final case object UnsupportedGrantType
      extends TokenError(
        error = "unsupported_grant_type",
        error_description = s"""
           |The authorization grant type is not supported by the
           |authorization server.
           """.stripMargin.replaceAll("\n", " ").trim
      )

  final case object InvalidScope
      extends TokenError(
        error = "invalid_scope",
        error_description = s"""
           |The requested scope is invalid, unknown, malformed, or
           |exceeds the scope granted by the resource owner.
           """.stripMargin.replaceAll("\n", " ").trim
      )
}

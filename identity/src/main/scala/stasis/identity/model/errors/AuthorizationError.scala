package stasis.identity.model.errors

import akka.http.scaladsl.model.Uri

sealed abstract class AuthorizationError(
  val error: String,
  val error_description: String,
  val state: String
) {
  def asQuery: Uri.Query = Uri.Query(
    "error" -> error,
    "state" -> state
  )
}

object AuthorizationError {
  final case class InvalidRequest(withState: String)
      extends AuthorizationError(
        error = "invalid_request",
        error_description = s"""
             |The request is missing a required parameter, includes an
             |invalid parameter value, includes a parameter more than
             |once, or is otherwise malformed.
           """.stripMargin.replaceAll("\n", " ").trim,
        state = withState
      )

  final case class UnauthorizedClient(withState: String)
      extends AuthorizationError(
        error = "unauthorized_client",
        error_description = s"""
             |The client is not authorized to request an authorization
             |code using this method.
           """.stripMargin.replaceAll("\n", " ").trim,
        state = withState
      )

  final case class AccessDenied(withState: String)
      extends AuthorizationError(
        error = "access_denied",
        error_description = s"""
             |The resource owner or authorization server denied the
             |request.
           """.stripMargin.replaceAll("\n", " ").trim,
        state = withState
      )

  final case class UnsupportedResponseType(withState: String)
      extends AuthorizationError(
        error = "unsupported_response_type",
        error_description = s"""
             |The authorization server does not support obtaining an
             |authorization code using this method.
           """.stripMargin.replaceAll("\n", " ").trim,
        state = withState
      )

  final case class InvalidScope(withState: String)
      extends AuthorizationError(
        error = "invalid_scope",
        error_description = s"""
             |The requested scope is invalid, unknown, or malformed.
           """.stripMargin.replaceAll("\n", " ").trim,
        state = withState
      )

  final case class ServerError(withState: String)
      extends AuthorizationError(
        error = "server_error",
        error_description = s"""
             |The authorization server encountered an unexpected
             |condition that prevented it from fulfilling the request.
           """.stripMargin.replaceAll("\n", " ").trim,
        state = withState
      )

  final case class TemporarilyUnavailable(withState: String)
      extends AuthorizationError(
        error = "temporarily_unavailable",
        error_description = s"""
             |The authorization server is currently unable to handle
             |the request due to a temporary overloading or maintenance
             |of the server
           """.stripMargin.replaceAll("\n", " ").trim,
        state = withState
      )
}

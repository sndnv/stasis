package stasis.identity.model

sealed trait GrantType

object GrantType {
  case object AuthorizationCode extends GrantType
  case object ClientCredentials extends GrantType
  case object Implicit extends GrantType
  case object RefreshToken extends GrantType
  case object Password extends GrantType
}

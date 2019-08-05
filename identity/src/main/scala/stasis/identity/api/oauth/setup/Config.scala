package stasis.identity.api.oauth.setup

final case class Config(
  realm: String,
  refreshTokensAllowed: Boolean
)

package stasis.identity.model.tokens

import stasis.identity.model.Seconds

final case class AccessTokenWithExpiration(token: AccessToken, expiration: Seconds)

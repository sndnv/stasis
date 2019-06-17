package stasis.identity.model.tokens.generators

import stasis.identity.model.tokens.RefreshToken

trait RefreshTokenGenerator {
  def generate(): RefreshToken
}

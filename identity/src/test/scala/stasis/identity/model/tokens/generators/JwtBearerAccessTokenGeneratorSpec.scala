package stasis.identity.model.tokens.generators

import stasis.layers.UnitSpec
import stasis.layers.security.mocks.MockJwksGenerators

class JwtBearerAccessTokenGeneratorSpec extends UnitSpec with JwtBearerAccessTokenGeneratorBehaviour {
  "A JwtBearerAccessTokenGenerator" should behave like jwtBearerAccessTokenGenerator(
    withKeyType = "RSA",
    withJwk = MockJwksGenerators.generateRandomRsaKey(Some("rsa-0"))
  )

  it should behave like jwtBearerAccessTokenGenerator(
    withKeyType = "EC",
    withJwk = MockJwksGenerators.generateRandomEcKey(Some("ec-0"))
  )

  it should behave like jwtBearerAccessTokenGenerator(
    withKeyType = "Secret",
    withJwk = MockJwksGenerators.generateRandomSecretKey(Some("oct-0"))
  )
}

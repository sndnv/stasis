package stasis.identity.model.tokens.generators

import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.security.mocks.MockJwksGenerator

class JwtBearerAccessTokenGeneratorSpec extends UnitSpec with JwtBearerAccessTokenGeneratorBehaviour {
  "A JwtBearerAccessTokenGenerator" should behave like jwtBearerAccessTokenGenerator(
    withKeyType = "RSA",
    withJwk = MockJwksGenerator.generateRandomRsaKey(Some("rsa-0"))
  )

  it should behave like jwtBearerAccessTokenGenerator(
    withKeyType = "EC",
    withJwk = MockJwksGenerator.generateRandomEcKey(Some("ec-0"))
  )

  it should behave like jwtBearerAccessTokenGenerator(
    withKeyType = "Secret",
    withJwk = MockJwksGenerator.generateRandomSecretKey(Some("oct-0"))
  )
}

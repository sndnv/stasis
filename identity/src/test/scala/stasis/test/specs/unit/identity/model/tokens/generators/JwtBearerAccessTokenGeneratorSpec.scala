package stasis.test.specs.unit.identity.model.tokens.generators

import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.MockJwksGenerators

class JwtBearerAccessTokenGeneratorSpec extends AsyncUnitSpec with JwtBearerAccessTokenGeneratorBehaviour {
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

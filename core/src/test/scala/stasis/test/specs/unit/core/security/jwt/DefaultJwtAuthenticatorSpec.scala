package stasis.test.specs.unit.core.security.jwt

import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.MockJwksGenerators

class DefaultJwtAuthenticatorSpec extends AsyncUnitSpec with JwtAuthenticatorBehaviour {
  "A DefaultJwtAuthenticator" should behave like authenticator(
    withKeyType = "RSA",
    withJwk = MockJwksGenerators.generateRandomRsaKey(Some("rsa-0"))
  )

  it should behave like authenticator(
    withKeyType = "EC",
    withJwk = MockJwksGenerators.generateRandomEcKey(Some("ec-0"))
  )

  it should behave like authenticator(
    withKeyType = "Secret",
    withJwk = MockJwksGenerators.generateRandomSecretKey(Some("oct-0"))
  )
}

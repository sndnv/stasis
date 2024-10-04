package stasis.layers.security.jwt

import stasis.layers.UnitSpec
import stasis.layers.security.mocks.MockJwksGenerators

class DefaultJwtAuthenticatorSpec extends UnitSpec with JwtAuthenticatorBehaviour {
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

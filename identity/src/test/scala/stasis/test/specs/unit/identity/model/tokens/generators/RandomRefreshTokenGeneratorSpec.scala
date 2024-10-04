package stasis.test.specs.unit.identity.model.tokens.generators

import stasis.identity.model.tokens.generators.RandomRefreshTokenGenerator
import stasis.layers.UnitSpec

class RandomRefreshTokenGeneratorSpec extends UnitSpec {
  "A RandomRefreshTokenGenerator" should "generate random refresh tokens" in {
    val tokenSize = 32
    val generator = new RandomRefreshTokenGenerator(tokenSize)
    val token = generator.generate()

    token.value.length should be(tokenSize)
    token.value.matches("^[A-Za-z0-9]+$") should be(true)
  }
}

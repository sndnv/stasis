package stasis.identity.model.codes.generators

import stasis.layers.UnitSpec

class DefaultAuthorizationCodeGeneratorSpec extends UnitSpec {
  "A DefaultAuthorizationCodeGenerator" should "generate random authorization codes" in {
    val codeSize = 32
    val generator = new DefaultAuthorizationCodeGenerator(codeSize)
    val code = generator.generate()

    code.value.length should be(codeSize)
    code.value.matches("^[A-Za-z0-9]+$") should be(true)
  }
}

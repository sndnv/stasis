package stasis.test.specs.unit.identity.model.apis

import stasis.layers.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class ApiSpec extends UnitSpec {
  "An Api" should "validate its identifier" in {
    val api = Generators.generateApi
    an[IllegalArgumentException] should be thrownBy api.copy(id = "")
    an[IllegalArgumentException] should be thrownBy api.copy(id = "abc~def")
  }
}

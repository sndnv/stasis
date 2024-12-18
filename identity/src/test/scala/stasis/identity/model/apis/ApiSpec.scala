package stasis.identity.model.apis

import stasis.identity.model.Generators
import stasis.layers.UnitSpec

class ApiSpec extends UnitSpec {
  "An Api" should "validate its fields" in {
    val api = Generators.generateApi
    an[IllegalArgumentException] should be thrownBy api.copy(id = "")
    an[IllegalArgumentException] should be thrownBy api.copy(id = "abc~def")
  }
}

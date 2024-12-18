package stasis.layers.service.bootstrap

import stasis.layers.UnitSpec

class BootstrapResultSpec extends UnitSpec {
  "A MigrationResult" should "support providing an empty result" in {
    val empty = BootstrapResult.empty

    empty.found should be(0)
    empty.created should be(0)
  }
  it should "support addition" in {
    val resultA = BootstrapResult(found = 3, created = 2)
    val resultB = BootstrapResult(found = 1, created = 1)
    val resultC = BootstrapResult(found = 0, created = 0)

    val finalResult = resultA + resultB + resultC

    finalResult should be(BootstrapResult(found = 4, created = 3))
  }
}

package stasis.layers.persistence.migration

import stasis.layers.UnitSpec

class MigrationResultSpec extends UnitSpec {
  "A MigrationResult" should "support providing an empty result" in {
    val empty = MigrationResult.empty

    empty.found should be(0)
    empty.executed should be(0)
  }

  it should "support addition" in {
    val resultA = MigrationResult(found = 3, executed = 2)
    val resultB = MigrationResult(found = 1, executed = 1)
    val resultC = MigrationResult(found = 0, executed = 0)

    val finalResult = resultA + resultB + resultC

    finalResult should be(MigrationResult(found = 4, executed = 3))
  }
}

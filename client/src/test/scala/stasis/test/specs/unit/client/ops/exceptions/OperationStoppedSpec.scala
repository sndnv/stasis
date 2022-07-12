package stasis.test.specs.unit.client.ops.exceptions

import stasis.client.ops.exceptions.OperationStopped
import stasis.test.specs.unit.UnitSpec

class OperationStoppedSpec extends UnitSpec {
  "An OperationStopped" should "support extraction from a throwable" in {
    val failures = Seq(
      new RuntimeException("Test failure #1"),
      OperationStopped("Test failure #2"),
      new IllegalArgumentException("Test failure #3"),
      OperationStopped("Test failure #4"),
      new IllegalArgumentException("Test failure #5", OperationStopped("Test failure #6")),
      new IllegalArgumentException("Test failure #7"),
      OperationStopped("Test failure #8"),
      new RuntimeException(
        "Test failure #9",
        new IllegalArgumentException("Test failure #10", OperationStopped("Test failure #11"))
      )
    )

    failures.collect { case OperationStopped(e) => e }.map(_.getMessage) should be(
      Seq(
        "Test failure #2",
        "Test failure #4",
        "Test failure #6",
        "Test failure #8",
        "Test failure #11"
      )
    )
  }
}

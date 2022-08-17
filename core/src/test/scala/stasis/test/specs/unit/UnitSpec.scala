package stasis.test.specs.unit

import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

trait UnitSpec extends AnyFlatSpecLike with Matchers {
  def withRetry(f: => Assertion): Assertion =
    withRetry(times = 2)(f = f)

  def withRetry(times: Int)(f: => Assertion): Assertion =
    try {
      f
    } catch {
      case e if times > 0 =>
        alert(s"Test failed with [${e.getMessage}]; retrying...")
        withRetry(times = times - 1)(f = f)
    }
}

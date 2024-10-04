package stasis.test.specs.unit.client.service.components.internal

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import stasis.client.service.components.internal.FutureOps
import stasis.test.specs.unit.AsyncUnitSpec

class FutureOpsSpec extends AsyncUnitSpec with FutureOps {
  "FutureOps" should "support wrapping operations into futures" in {
    val a = 1
    val b = 0

    for {
      resultA <- (5 / a).future
      resultB <- (5 / b).future.failed
    } yield {
      resultA should be(5)
      resultB.getMessage should be("/ by zero")
    }
  }

  they should "support wrapping try-based operations into futures" in {
    val a = 1
    val b = 0

    for {
      resultA <- Try(5 / a).future
      resultB <- Try(5 / b).future.failed
    } yield {
      resultA should be(5)
      resultB.getMessage should be("/ by zero")
    }
  }

  they should "support transforming future failures" in {
    val expectedMessage = "other failure"

    Future
      .failed(new RuntimeException("test failure"))
      .transformFailureTo(transformer = _ => new RuntimeException(expectedMessage))
      .failed
      .map { e =>
        e.getMessage should be(expectedMessage)
      }
  }

  override implicit val ec: ExecutionContext =
    scala.concurrent.ExecutionContext.global
}

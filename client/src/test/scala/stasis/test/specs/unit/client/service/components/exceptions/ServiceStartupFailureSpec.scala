package stasis.test.specs.unit.client.service.components.exceptions

import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.test.specs.unit.UnitSpec

class ServiceStartupFailureSpec extends UnitSpec {
  "A ServiceStartupFailure" should "support transforming failures caused by credentials retrieval/usage" in {
    val actualFailure = ServiceStartupFailure.credentials(failure)
    actualFailure.cause should be("credentials")
    actualFailure.message should be(expectedMessage)
  }

  it should "support transforming failures caused by file operations" in {
    val actualFailure = ServiceStartupFailure.file(failure)
    actualFailure.cause should be("file")
    actualFailure.message should be(expectedMessage)
  }

  it should "support transforming failures caused by token retrieval" in {
    val actualFailure = ServiceStartupFailure.token(failure)
    actualFailure.cause should be("token")
    actualFailure.message should be(expectedMessage)
  }

  it should "support transforming failures caused by (mis)configuration" in {
    val actualFailure = ServiceStartupFailure.config(failure)
    actualFailure.cause should be("config")
    actualFailure.message should be(expectedMessage)
  }

  it should "support transforming failures caused by endpoint API" in {
    val actualFailure = ServiceStartupFailure.api(failure)
    actualFailure.cause should be("api")
    actualFailure.message should be(expectedMessage)
  }

  it should "support transforming unknown failures" in {
    val actualFailure = ServiceStartupFailure.unknown(failure)
    actualFailure.cause should be("unknown")
    actualFailure.message should be(expectedMessage)
  }

  private val failure = new RuntimeException("test failure")
  private val expectedMessage = "RuntimeException: test failure"
}

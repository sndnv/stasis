package stasis.test.specs.unit.client.service

import scala.util.Success

import stasis.client.service.ApplicationConfiguration
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class ApplicationConfigurationSpec extends AsyncUnitSpec with ResourceHelpers {
  "ApplicationConfiguration" should "only keep non-empty, non-comment lines" in {
    ApplicationConfiguration.keepLine(line = "") should be(false)
    ApplicationConfiguration.keepLine(line = "# comment") should be(false)
    ApplicationConfiguration.keepLine(line = "// comment") should be(false)
    ApplicationConfiguration.keepLine(line = "not a comment") should be(true)
  }

  it should "support loading entries from a file" in {
    val expectedLines = Seq(
      "line-01",
      "line-02",
      "line-03"
    )

    ApplicationConfiguration
      .parseEntries(file = "/ops/scheduling/test.file".asTestResource)(parse = (line, _) => Success(line))
      .map { actualLines =>
        actualLines should be(expectedLines)
      }
  }
}

package stasis.layers.telemetry

import java.time.Instant

import stasis.layers.UnitSpec

class ApplicationInformationSpec extends UnitSpec {
  "A none ApplicationInformation" should "provide no information" in {
    ApplicationInformation.none.name should be("none")
    ApplicationInformation.none.version should be("none")
    ApplicationInformation.none.buildTime should be(0L)
    ApplicationInformation.none.asString() should be("none;none;0")
  }

  "A valid ApplicationInformation" should "provide information" in {
    val now = Instant.now().toEpochMilli

    val app = new ApplicationInformation {
      override def name: String = "test-name"
      override def version: String = "test-version"
      override def buildTime: Long = now
    }

    app.name should be("test-name")
    app.version should be("test-version")
    app.buildTime should be(now)
    app.asString() should be(s"test-name;test-version;$now")
  }
}

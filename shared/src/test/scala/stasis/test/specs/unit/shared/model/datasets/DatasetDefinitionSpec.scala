package stasis.test.specs.unit.shared.model.datasets

import scala.concurrent.duration._

import io.github.sndnv.layers.testing.UnitSpec

import stasis.shared.model.datasets.DatasetDefinition
import stasis.test.specs.unit.shared.model.Generators

class DatasetDefinitionSpec extends UnitSpec {
  "A DatasetDefinition" should "validate its parameters" in {
    intercept[IllegalArgumentException](Generators.generateDefinition.copy(redundantCopies = 0)).getMessage should include(
      "Dataset definition redundant copies must be larger than 0"
    )
  }

  "A DatasetDefinition Retention" should "load itself from config (at-most policy)" in {
    val expected = DatasetDefinition.Retention(
      policy = DatasetDefinition.Retention.Policy.AtMost(versions = 3),
      duration = 3.hours
    )

    val actual = DatasetDefinition.Retention(config = config.getConfig("at-most"))

    actual should be(expected)
  }

  it should "load itself from config (latest-only policy)" in {
    val expected = DatasetDefinition.Retention(
      policy = DatasetDefinition.Retention.Policy.LatestOnly,
      duration = 4.hours
    )

    val actual = DatasetDefinition.Retention(config = config.getConfig("latest-only"))

    actual should be(expected)
  }

  it should "load itself from config (all policy)" in {
    val expected = DatasetDefinition.Retention(
      policy = DatasetDefinition.Retention.Policy.All,
      duration = 5.hours
    )

    val actual = DatasetDefinition.Retention(config = config.getConfig("all"))

    actual should be(expected)
  }

  "A DatasetDefinition Retention Policy" should "validate its parameters" in {
    withClue("for 'at-most' policy") {
      intercept[IllegalArgumentException](DatasetDefinition.Retention.Policy.AtMost(versions = 0)).getMessage should include(
        "Policy versions must be larger than 0"
      )
    }
  }

  it should "convert itself to string" in {
    DatasetDefinition.Retention.Policy.AtMost(versions = 42).toString should be("at-most, versions=42")
    DatasetDefinition.Retention.Policy.LatestOnly.toString should be("latest-only")
    DatasetDefinition.Retention.Policy.All.toString should be("all")
  }

  private val config = com.typesafe.config.ConfigFactory
    .load()
    .getConfig("stasis.test.shared.model.datasets.definitions.retention.policies")
}

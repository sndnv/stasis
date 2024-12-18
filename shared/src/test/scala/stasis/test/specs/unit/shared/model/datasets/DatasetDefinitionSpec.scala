package stasis.test.specs.unit.shared.model.datasets

import stasis.layers.UnitSpec
import stasis.shared.model.datasets.DatasetDefinition
import stasis.test.specs.unit.shared.model.Generators

class DatasetDefinitionSpec extends UnitSpec {
  "A DatasetDefinition" should "validate its parameters" in {
    intercept[IllegalArgumentException](Generators.generateDefinition.copy(redundantCopies = 0)).getMessage should include(
      "Dataset definition redundant copies must be larger than 0"
    )
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
}

package stasis.test.specs.unit.server.model.datasets

import stasis.server.model.datasets.DatasetDefinitionStoreSerdes
import stasis.shared.model.datasets.DatasetDefinition
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.shared.model.Generators

class DatasetDefinitionStoreSerdesSpec extends UnitSpec {
  "DatasetDefinitionStoreSerdes" should "serialize and deserialize keys" in {
    val definition = DatasetDefinition.generateId()

    val serialized = DatasetDefinitionStoreSerdes.serializeKey(definition)
    val deserialized = DatasetDefinitionStoreSerdes.deserializeKey(serialized)

    deserialized should be(definition)
  }

  they should "serialize and deserialize values" in {
    val definition = Generators.generateDefinition

    val serialized = DatasetDefinitionStoreSerdes.serializeValue(definition)
    val deserialized = DatasetDefinitionStoreSerdes.deserializeValue(serialized)

    deserialized should be(definition)
  }
}

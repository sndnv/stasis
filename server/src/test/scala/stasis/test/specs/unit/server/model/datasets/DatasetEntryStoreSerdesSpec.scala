package stasis.test.specs.unit.server.model.datasets

import stasis.server.model.datasets.DatasetEntryStoreSerdes
import stasis.shared.model.datasets.DatasetEntry
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.shared.model.Generators

class DatasetEntryStoreSerdesSpec extends UnitSpec {
  "DatasetEntryStoreSerdes" should "serialize and deserialize keys" in {
    val entry = DatasetEntry.generateId()

    val serialized = DatasetEntryStoreSerdes.serializeKey(entry)
    val deserialized = DatasetEntryStoreSerdes.deserializeKey(serialized)

    deserialized should be(entry)
  }

  they should "serialize and deserialize values" in {
    val entry = Generators.generateEntry

    val serialized = DatasetEntryStoreSerdes.serializeValue(entry)
    val deserialized = DatasetEntryStoreSerdes.deserializeValue(serialized)

    deserialized should be(entry)
  }
}

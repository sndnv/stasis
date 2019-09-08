package stasis.test.specs.unit.core.persistence.manifests

import stasis.core.packaging.Crate
import stasis.core.persistence.manifests.ManifestStoreSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.persistence.Generators

class ManifestStoreSerdesSpec extends UnitSpec {
  "ManifestStoreSerdes" should "serialize and deserialize keys" in {
    val crate = Crate.generateId()

    val serialized = ManifestStoreSerdes.serializeKey(crate)
    val deserialized = ManifestStoreSerdes.deserializeKey(serialized)

    deserialized should be(crate)
  }

  they should "serialize and deserialize values" in {
    val manifest = Generators.generateManifest

    val serialized = ManifestStoreSerdes.serializeValue(manifest)
    val deserialized = ManifestStoreSerdes.deserializeValue(serialized)

    deserialized should be(manifest)
  }
}

package stasis.test.specs.unit.shared.interop.datasets

import java.time.Instant
import java.util.UUID

import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.api.responses.DeletedDatasetEntry
import stasis.shared.model.datasets.DatasetEntry
import stasis.test.specs.unit.shared.interop.InteropSpec

class DatasetEntryInteropSpec extends InteropSpec {
  "DatasetEntry interop" should "decode and re-encode an entry" in {
    assert(
      domain = "datasets",
      resource = "DatasetEntry",
      matches = DatasetEntry(
        id = UUID.fromString("77eebe09-aea1-4e2e-9cd0-3b5919d43def"),
        definition = UUID.fromString("6c041cb6-94af-4649-9091-8e39d330527e"),
        device = UUID.fromString("caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b"),
        data = Set(UUID.fromString("d0c7ddc7-f639-4686-a3b5-465c61cdf94b")),
        metadata = UUID.fromString("6bb8d69c-5ae4-4876-9836-35e66a1c9ecb"),
        changes = Some(42L),
        size = Some(1048576L),
        created = Instant.parse("2026-01-22T09:00:00Z")
      )
    )
  }

  it should "decode and re-encode CreateDatasetEntry" in {
    assert(
      domain = "datasets",
      resource = "CreateDatasetEntry",
      matches = CreateDatasetEntry(
        definition = UUID.fromString("874e3e1e-a663-444d-b498-d771ecf5d0b7"),
        device = UUID.fromString("caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b"),
        data = Set(UUID.fromString("d0c7ddc7-f639-4686-a3b5-465c61cdf94b")),
        metadata = UUID.fromString("6bb8d69c-5ae4-4876-9836-35e66a1c9ecb"),
        changes = Some(13L),
        size = Some(524288L)
      )
    )
  }

  it should "decode and re-encode CreatedDatasetEntry" in {
    assert(
      domain = "datasets",
      resource = "CreatedDatasetEntry",
      matches = CreatedDatasetEntry(
        entry = UUID.fromString("17a89e29-0f3b-48f3-8f6c-dd939864e9a9")
      )
    )
  }

  it should "decode and re-encode DeletedDatasetEntry" in {
    assert(
      domain = "datasets",
      resource = "DeletedDatasetEntry",
      matches = DeletedDatasetEntry(existing = true)
    )
  }
}

package stasis.test.specs.unit.shared.interop.datasets

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.UpdateDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.api.responses.DeletedDatasetDefinition
import stasis.shared.model.datasets.DatasetDefinition
import stasis.test.specs.unit.shared.interop.InteropSpec

class DatasetDefinitionInteropSpec extends InteropSpec {
  "DatasetDefinition interop" should "decode and re-encode a definition" in {
    assert(
      domain = "datasets",
      resource = "DatasetDefinition",
      matches = DatasetDefinition(
        id = UUID.fromString("6c041cb6-94af-4649-9091-8e39d330527e"),
        info = "primary backup definition",
        device = UUID.fromString("caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b"),
        redundantCopies = 3,
        existingVersions = DatasetDefinition.Retention(
          policy = DatasetDefinition.Retention.Policy.AtMost(versions = 5),
          duration = 604800.seconds
        ),
        removedVersions = DatasetDefinition.Retention(
          policy = DatasetDefinition.Retention.Policy.LatestOnly,
          duration = 2592000.seconds
        ),
        created = Instant.parse("2026-01-15T10:30:00Z"),
        updated = Instant.parse("2026-01-20T14:45:00Z")
      )
    )
  }

  it should "decode and re-encode CreateDatasetDefinition" in {
    assert(
      domain = "datasets",
      resource = "CreateDatasetDefinition",
      matches = CreateDatasetDefinition(
        info = "new backup definition",
        device = UUID.fromString("caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b"),
        redundantCopies = 2,
        existingVersions = DatasetDefinition.Retention(
          policy = DatasetDefinition.Retention.Policy.All,
          duration = 7776000.seconds
        ),
        removedVersions = DatasetDefinition.Retention(
          policy = DatasetDefinition.Retention.Policy.AtMost(versions = 7),
          duration = 1209600.seconds
        )
      )
    )
  }

  it should "decode and re-encode UpdateDatasetDefinition" in {
    assert(
      domain = "datasets",
      resource = "UpdateDatasetDefinition",
      matches = UpdateDatasetDefinition(
        info = "updated backup definition",
        redundantCopies = 4,
        existingVersions = DatasetDefinition.Retention(
          policy = DatasetDefinition.Retention.Policy.LatestOnly,
          duration = 86400.seconds
        ),
        removedVersions = DatasetDefinition.Retention(
          policy = DatasetDefinition.Retention.Policy.AtMost(versions = 3),
          duration = 432000.seconds
        )
      )
    )
  }

  it should "decode and re-encode CreatedDatasetDefinition" in {
    assert(
      domain = "datasets",
      resource = "CreatedDatasetDefinition",
      matches = CreatedDatasetDefinition(
        definition = UUID.fromString("6d63a635-d012-4b28-9193-7bd870cc4093")
      )
    )
  }

  it should "decode and re-encode DeletedDatasetDefinition" in {
    assert(
      domain = "datasets",
      resource = "DeletedDatasetDefinition",
      matches = DeletedDatasetDefinition(existing = true)
    )
  }
}

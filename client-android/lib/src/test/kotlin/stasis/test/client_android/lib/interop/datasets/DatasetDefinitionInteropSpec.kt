package stasis.test.client_android.lib.interop.datasets

import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.test.client_android.lib.interop.InteropSpec
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DatasetDefinitionInteropSpec : InteropSpec({
    "DatasetDefinition interop" should {
        "decode and re-encode a definition" {
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
                        duration = Duration.ofSeconds(604800)
                    ),
                    removedVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.LatestOnly,
                        duration = Duration.ofSeconds(2592000)
                    ),
                    created = Instant.parse("2026-01-15T10:30:00Z"),
                    updated = Instant.parse("2026-01-20T14:45:00Z")
                )
            )
        }

        "decode and re-encode CreateDatasetDefinition" {
            assert(
                domain = "datasets",
                resource = "CreateDatasetDefinition",
                matches = CreateDatasetDefinition(
                    info = "new backup definition",
                    device = UUID.fromString("caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b"),
                    redundantCopies = 2,
                    existingVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.All,
                        duration = Duration.ofSeconds(7776000)
                    ),
                    removedVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.AtMost(versions = 7),
                        duration = Duration.ofSeconds(1209600)
                    )
                )
            )
        }

        "decode and re-encode UpdateDatasetDefinition" {
            assert(
                domain = "datasets",
                resource = "UpdateDatasetDefinition",
                matches = UpdateDatasetDefinition(
                    info = "updated backup definition",
                    redundantCopies = 4,
                    existingVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.LatestOnly,
                        duration = Duration.ofSeconds(86400)
                    ),
                    removedVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.AtMost(versions = 3),
                        duration = Duration.ofSeconds(432000)
                    )
                )
            )
        }

        "decode and re-encode CreatedDatasetDefinition" {
            assert(
                domain = "datasets",
                resource = "CreatedDatasetDefinition",
                matches = CreatedDatasetDefinition(
                    definition = UUID.fromString("6d63a635-d012-4b28-9193-7bd870cc4093")
                )
            )
        }
    }
})

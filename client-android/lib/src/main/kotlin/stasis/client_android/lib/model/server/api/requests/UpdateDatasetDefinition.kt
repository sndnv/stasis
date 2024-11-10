package stasis.client_android.lib.model.server.api.requests

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import stasis.client_android.lib.model.server.datasets.DatasetDefinition

@JsonClass(generateAdapter = true)
data class UpdateDatasetDefinition(
    val info: String,
    @Json(name = "redundant_copies")
    val redundantCopies: Int,
    @Json(name = "existing_versions")
    val existingVersions: DatasetDefinition.Retention,
    @Json(name = "removed_versions")
    val removedVersions: DatasetDefinition.Retention
)

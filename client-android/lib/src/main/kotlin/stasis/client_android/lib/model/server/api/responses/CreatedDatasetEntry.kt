package stasis.client_android.lib.model.server.api.responses

import com.squareup.moshi.JsonClass
import stasis.client_android.lib.model.server.datasets.DatasetEntryId

@JsonClass(generateAdapter = true)
data class CreatedDatasetEntry(val entry: DatasetEntryId)

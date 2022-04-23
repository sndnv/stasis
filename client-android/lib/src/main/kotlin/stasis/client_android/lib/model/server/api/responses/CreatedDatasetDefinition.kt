package stasis.client_android.lib.model.server.api.responses

import com.squareup.moshi.JsonClass
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

@JsonClass(generateAdapter = true)
data class CreatedDatasetDefinition(val definition: DatasetDefinitionId)

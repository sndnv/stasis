package stasis.client_android.lib.ops.search

import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try
import java.time.Instant

interface Search {
    suspend fun search(query: Regex, until: Instant?): Try<Result>

    data class Result(
        val definitions: Map<DatasetDefinitionId, DatasetDefinitionResult?>
    )

    data class DatasetDefinitionResult(
        val definitionInfo: String,
        val entryId: DatasetEntryId,
        val entryCreated: Instant,
        val matches: Map<String, FilesystemMetadata.EntityState>
    )
}

package stasis.client_android.lib.api.clients.caching

import stasis.client_android.lib.api.clients.CachedServerApiEndpointClient
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try

object CachingExtensions {
    fun ServerApiEndpointClient.statistics(): CacheRefreshHandler.Statistics? {
        return when (this) {
            is CachedServerApiEndpointClient -> this.refreshHandler.stats
            else -> null
        }
    }

    suspend fun ServerApiEndpointClient.refreshDatasetDefinitions(): Try<Unit> =
        refresh(target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions)

    suspend fun ServerApiEndpointClient.refreshDatasetEntries(definition: DatasetDefinitionId): Try<Unit> =
        refresh(target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = definition))

    suspend fun ServerApiEndpointClient.refreshDatasetDefinition(definition: DatasetDefinitionId): Try<Unit> =
        refresh(target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(definition = definition))

    suspend fun ServerApiEndpointClient.refreshDatasetEntry(entry: DatasetEntryId): Try<Unit> =
        refresh(target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(entry = entry))

    suspend fun ServerApiEndpointClient.refreshLatestDatasetEntry(definition: DatasetDefinitionId): Try<Unit> =
        refresh(target = CacheRefreshHandler.RefreshTarget.LatestDatasetEntry(definition = definition))

    private suspend fun ServerApiEndpointClient.refresh(target: CacheRefreshHandler.RefreshTarget): Try<Unit> {
        return when (this) {
            is CachedServerApiEndpointClient -> this.refreshHandler.refreshNow(target)
            else -> Try.Success(Unit) // do nothing
        }
    }
}

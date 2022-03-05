package stasis.client_android.api

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import stasis.client_android.activities.helpers.Common.getOrRenderFailure
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.ops.search.Search
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.LiveDataExtensions.liveData
import stasis.client_android.utils.LiveDataExtensions.optionalLiveData
import java.time.Instant
import javax.inject.Inject

class DatasetsViewModel @Inject constructor(
    application: Application,
    providerContextFactory: ProviderContext.Factory,
    private val datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition>,
    private val datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry>,
    private val datasetMetadataCache: Cache<DatasetEntryId, DatasetMetadata>
) : AndroidViewModel(application) {
    private val preferences: SharedPreferences =
        ConfigRepository.getPreferences(application.applicationContext)

    private val providerContext = providerContextFactory.getOrCreate(preferences).required()

    val self: DeviceId = providerContext.api.self

    fun createDefinition(
        request: CreateDatasetDefinition,
        f: (Try<CreatedDatasetDefinition>) -> Unit
    ) {
        viewModelScope.launch {
            f(providerContext.api.createDatasetDefinition(request))
        }
    }

    fun definitions(): LiveData<List<DatasetDefinition>> = liveData {
        providerContext.api.datasetDefinitions()
            .map { definitions -> definitions.sortedBy { it.info } }
            .getOrRenderFailure(withContext = getApplication()) ?: emptyList()
    }

    fun nonEmptyDefinitions(): LiveData<List<DatasetDefinition>> = liveData {
        providerContext.api.datasetDefinitions()
            .map { definitions ->
                definitions.filter { definition ->
                    providerContext.api.latestEntry(definition.id, until = null)
                        .map { it != null }
                        .getOrElse { false }
                }
            }
            .getOrRenderFailure(withContext = getApplication()) ?: emptyList()
    }

    fun definition(definition: DatasetDefinitionId): LiveData<DatasetDefinition> = liveData {
        datasetDefinitionsCache.getOrLoad(
            key = definition,
            load = {
                providerContext.api.datasetDefinition(definition)
                    .getOrRenderFailure(withContext = getApplication())
            }
        )
    }

    fun entries(forDefinition: DatasetDefinitionId): LiveData<List<DatasetEntry>> = liveData {
        providerContext.api.datasetEntries(definition = forDefinition)
            .map { entries -> entries.sortedByDescending { it.created } }
            .getOrRenderFailure(withContext = getApplication()) ?: emptyList()
    }

    fun entry(entry: DatasetEntryId): LiveData<DatasetEntry> = liveData {
        datasetEntriesCache.getOrLoad(
            key = entry,
            load = {
                providerContext.api.datasetEntry(entry)
                    .getOrRenderFailure(withContext = getApplication())
            }
        )
    }

    fun latestEntry(): LiveData<DatasetEntry?> = optionalLiveData {
        (providerContext.api.datasetDefinitions()
            .getOrRenderFailure(withContext = getApplication()) ?: emptyList())
            .mapNotNull {
                providerContext.api.latestEntry(definition = it.id, until = null)
                    .getOrRenderFailure(withContext = getApplication())
            }
            .maxByOrNull { it.created }
    }

    fun metadata(forEntry: DatasetEntry): LiveData<DatasetMetadata> = liveData {
        datasetMetadataCache.getOrLoad(
            key = forEntry.id,
            load = {
                providerContext.api.datasetMetadata(entry = forEntry)
                    .getOrRenderFailure(withContext = getApplication())
            }
        )
    }

    fun metadata(forDefinition: DatasetDefinitionId): LiveData<List<Pair<DatasetEntry, DatasetMetadata>>> =
        liveData {
            val entries = providerContext.api.datasetEntries(definition = forDefinition)
                .getOrRenderFailure(withContext = getApplication()) ?: emptyList()

            entries.sortedByDescending { it.created }.map { entry ->
                val metadata = datasetMetadataCache.getOrLoad(
                    key = entry.id,
                    load = {
                        providerContext.api.datasetMetadata(entry = entry)
                            .getOrRenderFailure(withContext = getApplication())
                    }
                )

                metadata?.let { entry to it }
            }.filterNotNull()
        }

    fun search(query: Regex, until: Instant?): LiveData<Search.Result> = liveData {
        providerContext.search.search(query, until)
            .getOrRenderFailure(withContext = getApplication())
    }
}

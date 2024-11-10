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
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.ops.search.Search
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

    fun updateDefinition(
        definition: DatasetDefinitionId,
        request: UpdateDatasetDefinition,
        f: (Try<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            f(providerContext.api.updateDatasetDefinition(definition, request))
        }
    }

    fun deleteDefinition(
        definition: DatasetDefinitionId,
        f: (Try<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            f(providerContext.api.deleteDatasetDefinition(definition))
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
        providerContext.api.datasetDefinition(definition)
            .getOrRenderFailure(withContext = getApplication())
    }

    fun entries(forDefinition: DatasetDefinitionId): LiveData<List<DatasetEntry>> = liveData {
        providerContext.api.datasetEntries(definition = forDefinition)
            .map { entries -> entries.sortedByDescending { it.created } }
            .getOrRenderFailure(withContext = getApplication()) ?: emptyList()
    }

    fun entry(entry: DatasetEntryId): LiveData<DatasetEntry> = liveData {
        providerContext.api.datasetEntry(entry)
            .getOrRenderFailure(withContext = getApplication())
    }

    fun deleteEntry(
        entry: DatasetEntryId,
        f: (Try<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            f(providerContext.api.deleteDatasetEntry(entry))
        }
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
        providerContext.api.datasetMetadata(entry = forEntry)
            .getOrRenderFailure(withContext = getApplication())
    }

    fun metadata(forDefinition: DatasetDefinitionId): LiveData<List<Pair<DatasetEntry, DatasetMetadata>>> =
        liveData {
            val entries = providerContext.api.datasetEntries(definition = forDefinition)
                .getOrRenderFailure(withContext = getApplication()) ?: emptyList()

            entries
                .sortedByDescending { it.created }
                .mapNotNull { entry ->
                    val metadata = providerContext.api.datasetMetadata(entry = entry)
                        .getOrRenderFailure(withContext = getApplication())
                    metadata?.let { entry to it }
                }
        }

    fun search(query: Regex, until: Instant?): LiveData<Search.Result> = liveData {
        providerContext.search.search(query, until)
            .getOrRenderFailure(withContext = getApplication())
    }
}

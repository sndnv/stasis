package stasis.client_android.lib.ops.search

import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Success
import java.time.Instant

class DefaultSearch(
    private val api: ServerApiEndpointClient
) : Search {
    override suspend fun search(query: Regex, until: Instant?): Try<Search.Result> {
        val regex = query.toPattern()

        val definitions = (api.datasetDefinitions().getOrElse { emptyList() })
            .map { definition -> definition to api.latestEntry(definition.id, until) }
            .map { (definition, entry) ->
                val entryMetadata = entry.flatMap {
                    it?.let {
                        api.datasetMetadata(it).map { metadata -> it to metadata }
                    } ?: Success(null)
                }

                definition to entryMetadata
            }
            .map { (definition, tryEntryMetadata) ->
                tryEntryMetadata.map { entryMetadata ->
                    when (entryMetadata) {
                        null -> definition.id to null
                        else -> {
                            val (entry, metadata) = entryMetadata

                            val matches = metadata.filesystem.entities.filter { (path, _) ->
                                regex.matcher(path).matches()
                            }

                            val result = Search.DatasetDefinitionResult(
                                definitionInfo = definition.info,
                                entryId = entry.id,
                                entryCreated = entry.created,
                                matches = matches
                            )

                            if (matches.isNotEmpty()) {
                                definition.id to result
                            } else {
                                definition.id to null
                            }
                        }
                    }
                }
            }

        return Try(seq = definitions).map { Search.Result(definitions = it.toMap()) }
    }
}

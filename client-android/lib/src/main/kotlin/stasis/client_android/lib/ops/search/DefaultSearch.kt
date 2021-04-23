package stasis.client_android.lib.ops.search

import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import java.time.Instant

class DefaultSearch(
    private val api: ServerApiEndpointClient
) : Search {
    override suspend fun search(query: Regex, until: Instant?): Search.Result {
        val regex = query.toPattern()

        val definitions = api.datasetDefinitions()
            .map { definition -> definition to api.latestEntry(definition.id, until) }
            .map { (definition, entry) -> definition to entry?.let { e -> e to api.datasetMetadata(e) } }
            .map { (definition, entryMetadata) ->
                when (entryMetadata) {
                    null -> definition.id to null
                    else -> {
                        val (entry, metadata) = entryMetadata

                        val matches = metadata.filesystem.entities.filter { (path, _) ->
                            regex.matcher(path.toAbsolutePath().toString()).matches()
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

        return Search.Result(definitions = definitions.toMap())
    }
}

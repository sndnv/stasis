package stasis.client_android.activities.fragments.backup

import stasis.client_android.lib.collection.rules.SourceUri

object EntryGrouping {
    fun groupEntitiesByScheme(entities: List<String>): List<Pair<String?, List<String>>> {
        val grouped: Map<String?, List<String>> = entities.groupBy { SourceUri.scheme(it) }

        val filesystem: List<Pair<String?, List<String>>> =
            grouped[null]?.let { listOf(null to it) }.orEmpty()

        val libraries: List<Pair<String?, List<String>>> =
            grouped.entries
                .filter { it.key != null }
                .sortedBy { it.key }
                .map { it.key to it.value }

        return filesystem + libraries
    }
}

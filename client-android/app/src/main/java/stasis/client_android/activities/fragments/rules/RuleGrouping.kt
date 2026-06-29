package stasis.client_android.activities.fragments.rules

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.SourceUri

object RuleGrouping {
    fun groupRulesByScheme(rules: List<Rule>): List<Pair<String?, List<Rule>>> {
        val grouped: Map<String?, List<Rule>> = rules.groupBy { SourceUri.scheme(it.source) }

        val filesystem: List<Pair<String?, List<Rule>>> =
            grouped[null]?.let { listOf(null to it) }.orEmpty()

        val libraries: List<Pair<String?, List<Rule>>> =
            grouped.entries
                .filter { it.key != null }
                .sortedBy { it.key }
                .map { it.key to it.value }

        return filesystem + libraries
    }
}

package stasis.client_android.activities.fragments.sources

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

object SourceRules {
    fun matching(rules: List<Rule>, scheme: String): List<Rule> =
        rules.filter { SourceUri.scheme(it.source) == scheme }

    fun isEnabled(rules: List<Rule>, scheme: String): Boolean =
        enabledSets(rules, scheme).isNotEmpty()

    fun isInDefault(rules: List<Rule>, scheme: String): Boolean =
        null in enabledSets(rules, scheme)

    fun allSets(rules: List<Rule>): Set<DatasetDefinitionId?> =
        rules.mapTo(mutableSetOf(null)) { it.definition }

    fun enabledSets(rules: List<Rule>, scheme: String): Set<DatasetDefinitionId?> =
        rules.filter { it.operation is Rule.Operation.Include && SourceUri.scheme(it.source) == scheme }
            .map { it.definition }
            .toSet()

    fun setsMissing(rules: List<Rule>, scheme: String): Set<DatasetDefinitionId?> =
        allSets(rules) - enabledSets(rules, scheme)

    fun defaultLibrarySchemes(rules: List<Rule>): Set<String> =
        rules.filter { it.definition == null && it.operation is Rule.Operation.Include }
            .mapNotNull { SourceUri.scheme(it.source) }
            .toSet()
}

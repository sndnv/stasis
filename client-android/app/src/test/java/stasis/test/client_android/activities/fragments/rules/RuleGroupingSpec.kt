package stasis.test.client_android.activities.fragments.rules

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Test
import stasis.client_android.activities.fragments.rules.RuleGrouping
import stasis.client_android.lib.collection.rules.Rule

class RuleGroupingSpec {
    private fun rule(id: Long, source: String): Rule =
        Rule(id = id, operation = Rule.Operation.Include, source = source, pattern = "*", definition = null)

    @Test
    fun groupRulesWithFilesystemFirstThenSchemesSorted() {
        val rules = listOf(
            rule(id = 0, source = "contacts:/"),
            rule(id = 1, source = "/storage/emulated/0"),
            rule(id = 2, source = "calendar:/"),
            rule(id = 3, source = "/storage/emulated/0/test")
        )

        val grouped = RuleGrouping.groupRulesByScheme(rules)

        assertThat(grouped.map { it.first }, equalTo(listOf(null, "calendar", "contacts")))
        assertThat(grouped[0].second.map { it.id }, equalTo(listOf(1L, 3L)))
        assertThat(grouped[1].second.map { it.id }, equalTo(listOf(2L)))
        assertThat(grouped[2].second.map { it.id }, equalTo(listOf(0L)))
    }

    @Test
    fun groupRulesWithNoFilesystemEntries() {
        val rules = listOf(rule(id = 0, source = "calendar:/"))

        val grouped = RuleGrouping.groupRulesByScheme(rules)

        assertThat(grouped.map { it.first }, equalTo(listOf("calendar")))
    }

    @Test
    fun groupEmptyRules() {
        assertThat(RuleGrouping.groupRulesByScheme(emptyList()).isEmpty(), equalTo(true))
    }
}

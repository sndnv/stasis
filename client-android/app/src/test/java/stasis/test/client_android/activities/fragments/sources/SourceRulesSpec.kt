package stasis.test.client_android.activities.fragments.sources

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import stasis.client_android.activities.fragments.sources.SourceRules
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import java.util.UUID

class SourceRulesSpec {
    private val definitionA: DatasetDefinitionId = UUID.fromString("e6c12064-eec2-4981-af21-184b989ef029")
    private val definitionB: DatasetDefinitionId = UUID.fromString("7984f830-74ff-4cde-a351-402d90ad8fad")

    private fun rule(
        id: Long,
        operation: Rule.Operation,
        source: String,
        definition: DatasetDefinitionId? = null
    ): Rule = Rule(id = id, operation = operation, source = source, pattern = "*", definition = definition)

    private val mixedRules = listOf(
        rule(id = 0, operation = Rule.Operation.Include, source = "calendar:/"),
        rule(id = 1, operation = Rule.Operation.Include, source = "/storage/emulated/0"),
        rule(id = 2, operation = Rule.Operation.Include, source = "calendar:/", definition = definitionA),
        rule(id = 3, operation = Rule.Operation.Include, source = "/storage/emulated/0", definition = definitionA),
        rule(id = 4, operation = Rule.Operation.Include, source = "contacts:/", definition = definitionB)
    )

    @Test
    fun reportSourceEnabledWhenAnIncludeRuleExistsForTheScheme() {
        val rules = listOf(
            rule(id = 0, operation = Rule.Operation.Include, source = "calendar:/"),
            rule(id = 1, operation = Rule.Operation.Include, source = "/storage/emulated/0")
        )

        assertThat(SourceRules.isEnabled(rules, "calendar"), equalTo(true))
    }

    @Test
    fun reportSourceDisabledWhenOnlyAnExcludeRuleExistsForTheScheme() {
        val rules = listOf(rule(id = 0, operation = Rule.Operation.Exclude, source = "calendar:/"))

        assertThat(SourceRules.isEnabled(rules, "calendar"), equalTo(false))
    }

    @Test
    fun reportSourceDisabledWhenNoRuleExistsForTheScheme() {
        val rules = listOf(rule(id = 0, operation = Rule.Operation.Include, source = "contacts:/"))

        assertThat(SourceRules.isEnabled(rules, "calendar"), equalTo(false))
    }

    @Test
    fun matchAllRulesForTheSchemeRegardlessOfOperation() {
        val rules = listOf(
            rule(id = 0, operation = Rule.Operation.Include, source = "calendar:/"),
            rule(id = 1, operation = Rule.Operation.Exclude, source = "calendar:/"),
            rule(id = 2, operation = Rule.Operation.Include, source = "contacts:/"),
            rule(id = 3, operation = Rule.Operation.Include, source = "/storage/emulated/0")
        )

        assertThat(SourceRules.matching(rules, "calendar").map { it.id }, equalTo(listOf(0L, 1L)))
    }

    @Test
    fun reportAllSetsIncludingTheDefault() {
        assertThat(SourceRules.allSets(mixedRules), equalTo(setOf(null, definitionA, definitionB)))
    }

    @Test
    fun reportEnabledSetsForTheScheme() {
        assertThat(SourceRules.enabledSets(mixedRules, "calendar"), equalTo(setOf(null, definitionA)))
        assertThat(SourceRules.enabledSets(mixedRules, "contacts"), equalTo(setOf(definitionB)))
    }

    @Test
    fun reportWhetherTheSchemeIsInTheDefaultSet() {
        assertThat(SourceRules.isInDefault(mixedRules, "calendar"), equalTo(true))
        assertThat(SourceRules.isInDefault(mixedRules, "contacts"), equalTo(false))
    }

    @Test
    fun reportSetsMissingTheScheme() {
        assertThat(SourceRules.setsMissing(mixedRules, "calendar"), equalTo(setOf(definitionB)))
        assertThat(SourceRules.setsMissing(mixedRules, "contacts"), equalTo(setOf(null, definitionA)))
    }

    @Test
    fun reportLibrarySchemesIncludedInTheDefaultSet() {
        assertThat(SourceRules.defaultLibrarySchemes(mixedRules), equalTo(setOf("calendar")))
    }
}

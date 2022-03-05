package stasis.client_android.lib.collection.rules

import stasis.client_android.lib.collection.rules.exceptions.RuleMatchingFailure
import stasis.client_android.lib.collection.rules.internal.FilesWalker
import stasis.client_android.lib.utils.NonFatal.nonFatal
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

data class Specification(
    val entries: Map<Path, Entry>,
    val failures: List<FailedMatch>
) {
    val explanation: Map<Path, List<Entry.Explanation>> by lazy {
        entries.map { (path, entry) -> path to entry.reason }.toMap()
    }

    val included: List<Path> by lazy { (includedEntries + includedParents).distinct() }

    val excluded: List<Path> by lazy { excludedEntries }

    val unmatched: List<Pair<Rule, Throwable>> by lazy {
        failures.map { it.rule to it.failure }
    }

    private val splitEntries by lazy {
        val (included, excluded) = entries.values.partition { it.operation == Rule.Operation.Include }

        val includedParents = included
            .flatMap { entry ->
                collectRelativeParents(from = entry.directory, to = entry.file)
            }
            .distinct()

        included.map { it.file } to includedParents to excluded.map { it.file }
    }

    private val includedEntries: List<Path> by lazy { splitEntries.first.first }
    private val includedParents: List<Path> by lazy { splitEntries.first.second }
    private val excludedEntries: List<Path> by lazy { splitEntries.second }

    data class Entry(
        val file: Path,
        val directory: Path,
        val operation: Rule.Operation,
        val reason: List<Explanation>
    ) {
        data class Explanation(
            val operation: Rule.Operation
        )
    }

    data class RuleMatcher(
        val rule: Rule,
        val matcher: PathMatcher
    )

    data class FailedMatch(
        val rule: Rule,
        val path: Path,
        val failure: Throwable
    )

    companion object {
        fun empty(): Specification = Specification(entries = emptyMap(), failures = emptyList())

        operator fun invoke(rules: List<Rule>): Specification = invoke(rules, filesystem = FileSystems.getDefault())

        operator fun invoke(rules: List<Rule>, filesystem: FileSystem): Specification {
            val (matchers, spec) = rules.fold(emptyList<RuleMatcher>() to empty()) { (matchers, spec), rule ->
                val (directory, matcher) = rule.asMatcher(withFilesystem = filesystem)

                try {
                    val result = FilesWalker.filter(start = directory, matcher = matcher)

                    val updated = if (result.isEmpty()) {
                        spec.copy(
                            failures = spec.failures + FailedMatch(
                                rule = rule,
                                path = directory,
                                failure = RuleMatchingFailure("Rule matched no files")
                            )
                        )
                    } else {
                        spec
                            .withMatches(result.matches, rule, directory)
                            .withFailures(result.failures, rule)
                    }

                    (matchers + RuleMatcher(rule, matcher)) to updated
                } catch (e: Throwable) {
                    val updated = spec.copy(
                        failures = spec.failures + FailedMatch(
                            rule = rule,
                            path = directory,
                            failure = e.nonFatal()
                        )
                    )

                    (matchers + RuleMatcher(rule, matcher)) to updated
                }
            }

            return spec.dropExcludedFailures(matchers)
        }

        private fun Rule.asMatcher(withFilesystem: FileSystem): Pair<Path, PathMatcher> {
            val ruleDirectory = if (this.directory.endsWith(withFilesystem.separator)) {
                this.directory
            } else {
                "${this.directory}${withFilesystem.separator}"
            }

            val directory = withFilesystem.getPath(ruleDirectory)

            val matcher = withFilesystem.getPathMatcher("glob:$ruleDirectory${this.pattern}")

            return directory to matcher
        }

        private fun Specification.withMatches(matches: List<Path>, rule: Rule, directory: Path): Specification {
            val updatedFiles = matches
                .map { file ->
                    val entry = when (val existing = this.entries[file]) {
                        null -> Entry(
                            file = file,
                            directory = directory,
                            operation = rule.operation,
                            reason = listOf(Entry.Explanation(rule.operation))
                        )
                        else -> existing.copy(
                            operation = rule.operation,
                            reason = existing.reason + Entry.Explanation(rule.operation)
                        )
                    }

                    file to entry
                }

            return this.copy(entries = this.entries + updatedFiles)
        }

        private fun Specification.withFailures(failures: Map<Path, Throwable>, rule: Rule): Specification =
            failures.toList().fold(this) { current, (path, failure) ->
                current.copy(
                    failures = current.failures + FailedMatch(
                        rule = rule,
                        path = path,
                        failure = failure
                    )
                )
            }

        private fun Specification.dropExcludedFailures(matchers: List<RuleMatcher>): Specification {
            val exclusionMatchers = matchers.filter { it.rule.operation == Rule.Operation.Exclude }

            return this.copy(
                failures = this.failures.filterNot { failure ->
                    exclusionMatchers.any {
                        it.matcher.matches(failure.path)
                    }
                }
            )
        }

        fun collectRelativeParents(from: Path, to: Path): List<Path> {
            tailrec fun collect(current: Path, collected: List<Path>): List<Path> {
                return if (current == from) {
                    collected
                } else {
                    val parent = current.parent
                    collect(
                        current = parent,
                        collected = collected + listOf(parent)
                    )
                }
            }

            return if (to.startsWith(from)) {
                collect(current = to, collected = emptyList())
            } else {
                emptyList()
            }
        }
    }
}

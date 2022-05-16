package stasis.client_android.lib.collection.rules

import stasis.client_android.lib.collection.rules.exceptions.RuleMatchingFailure
import stasis.client_android.lib.collection.rules.internal.FilesWalker
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.BackupTracker
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

    val includedEntries: List<Path> by lazy { splitEntries.first.first }
    val includedParents: List<Path> by lazy { splitEntries.first.second }
    val excludedEntries: List<Path> by lazy { splitEntries.second }

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

        fun tracked(
            operation: OperationId,
            rules: List<Rule>,
            tracker: BackupTracker
        ): Specification =
            invoke(
                rules = rules,
                onMatchIncluded = { path -> tracker.entityDiscovered(operation, path) },
                filesystem = FileSystems.getDefault()
            )

        operator fun invoke(
            rules: List<Rule>,
            onMatchIncluded: (Path) -> Unit
        ): Specification =
            invoke(rules = rules, onMatchIncluded = onMatchIncluded, filesystem = FileSystems.getDefault())

        operator fun invoke(
            rules: List<Rule>,
            onMatchIncluded: (Path) -> Unit,
            filesystem: FileSystem
        ): Specification {
            val (matchers, spec) = rules
                .groupBy { it.directory }
                .toList()
                .fold(emptyList<RuleMatcher>() to empty()) { (collected, spec), (groupedDirectory, rules) ->
                    val (directory, matchers) = rules.asMatchers(groupedDirectory, filesystem)

                    try {
                        val result = FilesWalker.filter(
                            start = directory,
                            matchers = matchers,
                            onMatchIncluded = onMatchIncluded
                        )

                        val updated = if (result.isEmpty()) {
                            spec.copy(
                                failures = spec.failures + rules.map { rule ->
                                    FailedMatch(
                                        rule = rule,
                                        path = directory,
                                        failure = RuleMatchingFailure("Rule matched no files")
                                    )
                                }
                            )
                        } else {
                            spec
                                .withMatches(result.matches, directory)
                                .withFailures(rules.first(), result.failures)
                        }

                        (collected + matchers.map { RuleMatcher(it.first, it.second) }) to updated
                    } catch (e: Throwable) {
                        val updated = spec.copy(
                            failures = spec.failures + FailedMatch(
                                rule = rules.first(),
                                path = directory,
                                failure = e.nonFatal()
                            )
                        )

                        (collected + matchers.map { RuleMatcher(it.first, it.second) }) to updated
                    }
                }

            return spec.dropExcludedFailures(matchers)
        }

        private fun List<Rule>.asMatchers(
            groupedDirectory: String,
            withFilesystem: FileSystem
        ): Pair<Path, List<Pair<Rule, PathMatcher>>> {
            val ruleDirectory = if (groupedDirectory.endsWith(withFilesystem.separator)) {
                groupedDirectory
            } else {
                "$groupedDirectory${withFilesystem.separator}"
            }

            val directory = withFilesystem.getPath(ruleDirectory)

            val matchers = this.map { rule ->
                rule to withFilesystem.getPathMatcher("glob:$ruleDirectory${rule.pattern}")
            }

            return directory to matchers
        }

        private fun Specification.withMatches(matches: Map<Rule, List<Path>>, directory: Path): Specification {
            val collected = this.entries.toMutableMap()

            matches.toList()
                .sortedBy { it.first.id }
                .fold(emptyList<Pair<Rule, Path>>()) { coll, (rule, files) ->
                    coll + files.map { file -> rule to file }
                }
                .forEach { (rule, file) ->
                    val entry = when (val existing = collected[file]) {
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

                    collected[file] = entry
                }

            return this.copy(entries = collected.toMap())
        }

        private fun Specification.withFailures(rule: Rule, failures: Map<Path, Throwable>): Specification =
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

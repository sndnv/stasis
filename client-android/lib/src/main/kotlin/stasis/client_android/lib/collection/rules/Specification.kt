package stasis.client_android.lib.collection.rules

import stasis.client_android.lib.collection.rules.exceptions.RuleMatchingFailure
import stasis.client_android.lib.utils.NonFatal.nonFatal
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

data class Specification(
    val entries: Map<Path, Entry>,
    val unmatched: List<Pair<Rule, Throwable>>
) {
    val explanation: Map<Path, List<Entry.Explanation>> by lazy {
        entries.map { (path, entry) -> path to entry.reason }.toMap()
    }

    val included: List<Path> by lazy { (includedEntries + includedParents).distinct() }

    val excluded: List<Path> by lazy { excludedEntries }

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
            val operation: Rule.Operation,
            val original: Rule.Original
        )
    }

    companion object {
        fun empty(): Specification = Specification(entries = emptyMap(), unmatched = emptyList())

        operator fun invoke(rules: List<Rule>): Specification = invoke(rules, filesystem = FileSystems.getDefault())

        operator fun invoke(rules: List<Rule>, filesystem: FileSystem): Specification =
            rules.fold(empty()) { spec, rule ->
                val ruleDirectory = if (rule.directory.endsWith(filesystem.separator)) {
                    rule.directory
                } else {
                    "${rule.directory}${filesystem.separator}"
                }

                val directory = filesystem.getPath(ruleDirectory)

                val matcher = filesystem.getPathMatcher("glob:$ruleDirectory${rule.pattern}")

                try {
                    val matchesStream = Files
                        .walk(directory)
                        .filter { path -> matcher.matches(path) }

                    val matches = matchesStream.use {
                        it.iterator().asSequence().toList()
                    }

                    if (matches.isEmpty()) {
                        spec.copy(unmatched = spec.unmatched + (rule to RuleMatchingFailure("Rule matched no files")))
                    } else {
                        val updatedFiles = matches
                            .map { file ->
                                val entry = when (val existing = spec.entries[file]) {
                                    null -> Entry(
                                        file = file,
                                        directory = directory,
                                        operation = rule.operation,
                                        reason = listOf(Entry.Explanation(rule.operation, rule.original))
                                    )
                                    else -> existing.copy(
                                        operation = rule.operation,
                                        reason = existing.reason + Entry.Explanation(rule.operation, rule.original)
                                    )
                                }

                                file to entry
                            }

                        spec.copy(entries = spec.entries + updatedFiles)
                    }
                } catch (e: Throwable) {
                    spec.copy(unmatched = spec.unmatched + (rule to e.nonFatal()))
                }
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

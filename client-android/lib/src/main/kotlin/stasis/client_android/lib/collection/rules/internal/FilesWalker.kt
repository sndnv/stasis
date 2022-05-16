package stasis.client_android.lib.collection.rules.internal

import stasis.client_android.lib.collection.rules.Rule
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.BasicFileAttributes

object FilesWalker {
    data class FilterResult(
        val matches: Map<Rule, List<Path>>,
        val failures: Map<Path, Throwable>
    ) {
        fun isEmpty(): Boolean = matches.isEmpty() && failures.isEmpty()
    }

    fun filter(
        start: Path,
        matchers: List<Pair<Rule, PathMatcher>>,
        onMatchIncluded: (Path) -> Unit
    ): FilterResult {
        val collected = mutableMapOf<Rule, MutableList<Path>>()
        val failures = mutableMapOf<Path, Throwable>()

        val sortedMatchers = matchers.sortedBy { it.first.id }

        Files.walkFileTree(
            start,
            object : FileVisitor<Path> {
                override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    var lastMatchedRule: Rule? = null

                    dir?.let {
                        sortedMatchers.forEach { (rule, matcher) ->
                            if (matcher.matches(it)) {
                                lastMatchedRule = rule
                                collected.getOrPut(rule) { mutableListOf() }.add(it)
                            }
                        }
                    }

                    return when (lastMatchedRule?.operation) {
                        is Rule.Operation.Exclude -> FileVisitResult.SKIP_SUBTREE
                        is Rule.Operation.Include -> {
                            dir?.let { onMatchIncluded(it) }
                            FileVisitResult.CONTINUE
                        }
                        else -> FileVisitResult.CONTINUE
                    }
                }

                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    var lastMatchedRule: Rule? = null

                    file?.let {
                        sortedMatchers.forEach { (rule, matcher) ->
                            if (matcher.matches(it)) {
                                lastMatchedRule = rule
                                collected.getOrPut(rule) { mutableListOf() }.add(it)
                            }
                        }
                    }

                    when (lastMatchedRule?.operation) {
                        is Rule.Operation.Include -> file?.let { onMatchIncluded(it) }
                        else -> Unit // do nothing
                    }

                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                    file?.let { path ->
                        exc?.let { failure ->
                            failures[path] = failure
                        }
                    }

                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                    dir?.let { path ->
                        exc?.let { failure ->
                            failures[path] = failure
                        }
                    }

                    return FileVisitResult.CONTINUE
                }
            }
        )

        return FilterResult(
            matches = collected,
            failures = failures
        )
    }
}

package stasis.client_android.lib.collection.rules.internal

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.BasicFileAttributes

object FilesWalker {
    data class FilterResult(
        val matches: List<Path>,
        val failures: Map<Path, Throwable>
    ) {
        fun isEmpty(): Boolean = matches.isEmpty() && failures.isEmpty()
    }

    fun filter(start: Path, matcher: PathMatcher): FilterResult {
        val collected = mutableListOf<Path>()
        val failures = mutableMapOf<Path, Throwable>()

        Files.walkFileTree(
            start,
            object : FileVisitor<Path> {
                override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    dir?.let {
                        if (matcher.matches(it)) {
                            collected.add(it)
                        }
                    }

                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    file?.let {
                        if (matcher.matches(it)) {
                            collected.add(it)
                        }
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

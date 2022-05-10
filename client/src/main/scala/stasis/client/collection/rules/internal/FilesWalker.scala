package stasis.client.collection.rules.internal

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object FilesWalker {
  final case class FilterResult(
    matches: Seq[Path],
    failures: Map[Path, Throwable]
  ) {
    def isEmpty: Boolean = matches.isEmpty && failures.isEmpty
  }

  def filter(start: Path, matcher: PathMatcher): FilterResult = {
    val collected = ListBuffer.empty[Path]
    val failures = mutable.HashMap.empty[Path, Throwable]

    val _ = Files.walkFileTree(
      start,
      new FileVisitor[Path] {
        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Option(dir).foreach { current =>
            if (matcher.matches(current)) {
              collected.append(current)
            }
          }

          FileVisitResult.CONTINUE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Option(file).foreach { current =>
            if (matcher.matches(current)) {
              collected.append(current)
            }
          }

          FileVisitResult.CONTINUE
        }

        override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
          Option(file).foreach { path =>
            Option(exc).foreach { failure =>
              failures.put(path, failure)
            }
          }

          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          Option(dir).foreach { path =>
            Option(exc).foreach { failure =>
              failures.put(path, failure)
            }
          }

          FileVisitResult.CONTINUE
        }
      }
    )

    FilterResult(
      matches = collected.toSeq,
      failures = failures.toMap
    )
  }
}

package stasis.client.collection.rules.internal

import stasis.client.collection.rules.Rule

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object FilesWalker {
  final case class FilterResult(
    matches: Map[IndexedRule, Seq[Path]],
    failures: Map[Path, Throwable]
  ) {
    def isEmpty: Boolean = matches.isEmpty && failures.isEmpty
  }

  def filter(start: Path, matchers: Seq[(IndexedRule, PathMatcher)], onMatchIncluded: Path => Unit): FilterResult = {
    val collected = mutable.HashMap.empty[IndexedRule, ListBuffer[Path]]
    val failures = mutable.HashMap.empty[Path, Throwable]

    val sortedMatchers = matchers.sortBy(_._1.index)

    val _ = Files.walkFileTree(
      start,
      new FileVisitor[Path] {
        @SuppressWarnings(Array("org.wartremover.warts.Var"))
        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          var lastMatchedRule: Option[IndexedRule] = None

          Option(dir).foreach { current =>
            sortedMatchers.foreach { case (rule, matcher) =>
              if (matcher.matches(current)) {
                lastMatchedRule = Some(rule)
                collected.getOrElseUpdate(rule, defaultValue = ListBuffer.empty[Path]).append(current)
              }
            }
          }

          lastMatchedRule.map(_.underlying.operation) match {
            case Some(Rule.Operation.Exclude) =>
              FileVisitResult.SKIP_SUBTREE

            case Some(Rule.Operation.Include) =>
              onMatchIncluded(dir)
              FileVisitResult.CONTINUE

            case _ =>
              FileVisitResult.CONTINUE
          }
        }

        @SuppressWarnings(Array("org.wartremover.warts.Var"))
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          var lastMatchedRule: Option[IndexedRule] = None

          Option(file).foreach { current =>
            sortedMatchers.foreach { case (rule, matcher) =>
              if (matcher.matches(current)) {
                lastMatchedRule = Some(rule)
                collected.getOrElseUpdate(rule, defaultValue = ListBuffer.empty[Path]).append(current)
              }
            }
          }

          lastMatchedRule.map(_.underlying.operation) match {
            case Some(Rule.Operation.Include) => onMatchIncluded(file)
            case _                            => () // do nothing
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
      matches = collected.view.mapValues(_.toSeq).toMap,
      failures = failures.toMap
    )
  }
}

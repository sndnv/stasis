package stasis.layers.files

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.BasicFileAttributes

import org.slf4j.Logger

class FilteringFileVisitor(matcher: PathMatcher) extends FileVisitor[Path] {
  private val collected = scala.collection.mutable.ListBuffer.empty[Path]
  private val collectedFailures = scala.collection.mutable.ListBuffer.empty[(Path, String)]

  def matched: Seq[Path] = collected.toSeq

  def failed: Seq[(Path, String)] = collectedFailures.toSeq

  def walk(start: Path): FilteringFileVisitor.Result = {
    val _ = Files.walkFileTree(start, this)
    FilteringFileVisitor.Result(matched = matched, failed = failed)
  }

  override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
    Option(dir).filter(matcher.matches).foreach { current => collected.append(current) }
    FileVisitResult.CONTINUE
  }

  override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
    Option(file).filter(matcher.matches).foreach { current => collected.append(current) }
    FileVisitResult.CONTINUE
  }

  override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
    val _ = collectedFailures.append(file -> s"${exc.getClass.getSimpleName} - ${exc.getMessage}")
    FileVisitResult.CONTINUE
  }

  override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult =
    FileVisitResult.CONTINUE
}

object FilteringFileVisitor {
  def apply(matcher: PathMatcher): FilteringFileVisitor =
    new FilteringFileVisitor(matcher)

  final case class Result(
    matched: Seq[Path],
    failed: Seq[(Path, String)]
  ) {
    def successful(log: Logger): Seq[Path] = {
      failed.foreach { case (path, failure) =>
        log.debug("Visiting entity [{}] failed with [{}]", path.normalize().toAbsolutePath.toString, failure)
      }

      matched
    }
  }
}

package stasis.client.service

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source
import scala.util.Try

object ApplicationConfiguration {
  object Comment {
    final val Hash: String = "#"
    final val Slash: String = "//"
  }

  def parseEntries[T](file: Path)(parse: (String, Int) => Try[T])(implicit ec: ExecutionContext): Future[Seq[T]] =
    Future {
      val source = Source.fromFile(file.toFile)
      val result = source.getLines().toList
      source.close
      result
    }.flatMap { lines =>
      val result = lines
        .map(_.trim)
        .zipWithIndex
        .filter { case (line, _) => keepLine(line) }
        .map { case (line, number) => parse(line, number) }
        .foldLeft(Try(Seq.empty[T])) { case (tryCollected, tryEntry) =>
          tryCollected.flatMap { collected =>
            tryEntry.map(entry => collected :+ entry)
          }
        }

      Future.fromTry(result)
    }

  def keepLine(line: String): Boolean =
    line.nonEmpty && !line.startsWith(Comment.Hash) && !line.startsWith(Comment.Slash)
}

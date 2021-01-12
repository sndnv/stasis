package stasis.client.ops.scheduling

import java.nio.file.Path

import stasis.client.collection.rules.Rule

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Try

object SchedulingConfig {
  object Comment {
    final val Hash: String = "#"
    final val Slash: String = "//"
  }

  def rules(file: Path)(implicit ec: ExecutionContext): Future[Seq[Rule]] =
    SchedulingConfig.parseEntries(file)(Rule.apply)

  def schedules(file: Path)(implicit ec: ExecutionContext): Future[Seq[OperationScheduleAssignment]] =
    SchedulingConfig.parseEntries(file)(OperationScheduleAssignment.apply)

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

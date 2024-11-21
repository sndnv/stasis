package stasis.client.collection.rules

import java.nio.file.Path
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.matching.Regex

import stasis.client.service.ApplicationDirectory
import stasis.client.service.ApplicationConfiguration
import stasis.shared.model.datasets.DatasetDefinition

final case class RuleSet(definitions: Map[Option[DatasetDefinition.Id], Seq[Rule]]) {
  def default(): Seq[Rule] =
    definitions.get(None) match {
      case Some(result) => result
      case None         => throw new IllegalStateException("No default rules were found")
    }

  def forDefinitionOrDefault(definition: DatasetDefinition.Id): Seq[Rule] =
    definitions.get(Some(definition)).orElse(definitions.get(None)) match {
      case Some(result) =>
        result

      case None =>
        throw new IllegalStateException(s"No default rules or rules for definition [${definition.toString}] were found")
    }
}

object RuleSet {
  trait Factory {
    def latest(): Future[RuleSet]
  }

  object Factory {
    def apply(directory: ApplicationDirectory, pattern: String)(implicit ec: ExecutionContext): Factory =
      () => directory.requireFiles(pattern).flatMap(fromFiles)
  }

  def fromFiles(files: Seq[Path])(implicit ec: ExecutionContext): Future[RuleSet] =
    files
      .foldLeft(Future.successful(Map.empty[Option[DatasetDefinition.Id], Seq[Rule]])) { case (collected, current) =>
        for {
          collected <- collected
          result <- fromFile(current)
        } yield {
          collected + result
        }
      }
      .map(RuleSet.apply)

  private def fromFile(file: Path)(implicit ec: ExecutionContext): Future[(Option[DatasetDefinition.Id], Seq[Rule])] =
    ApplicationConfiguration
      .parseEntries[Either[DatasetDefinition.Id, Rule]](file) { case (line, lineNumber) =>
        line match {
          case DefinitionRegex(definition) => Try(Left(UUID.fromString(definition)))
          case _                           => Rule(line = line, lineNumber = lineNumber).map(Right.apply)
        }
      }
      .map { result =>
        val (definitions, rules) = result.partitionMap(identity)
        (definitions.lastOption, rules)
      }

  private val DefinitionRegex: Regex = """definition\s*=\s*([a-zA-Z0-9-]*).*""".r
}

package stasis.client.ops.search

import java.nio.file.Path
import java.time.Instant
import java.util.regex.Pattern

import stasis.client.model.FilesystemMetadata
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import scala.concurrent.Future
import scala.util.Try

trait Search {
  def search(query: Search.Query, until: Option[Instant]): Future[Search.Result]
}

object Search {
  final case class Query(original: String, pattern: Pattern)

  object Query {
    def apply(query: String): Query = {
      val pattern = if (PlainChars.matcher(query).matches()) {
        Pattern.compile(s".*$query.*", Pattern.CASE_INSENSITIVE)
      } else {
        Try {
          Pattern.compile(query, Pattern.CASE_INSENSITIVE)
        }.getOrElse {
          Pattern.compile(query, Pattern.LITERAL)
        }
      }

      Query(original = query, pattern = pattern)
    }

    private val PlainChars: Pattern = Pattern.compile("[\\w _-]*", Pattern.CASE_INSENSITIVE)
  }

  final case class Result(
    definitions: Map[DatasetDefinition.Id, Option[DatasetDefinitionResult]]
  )

  final case class DatasetDefinitionResult(
    definitionInfo: String,
    entryId: DatasetEntry.Id,
    entryCreated: Instant,
    matches: Map[Path, FilesystemMetadata.EntityState]
  )
}

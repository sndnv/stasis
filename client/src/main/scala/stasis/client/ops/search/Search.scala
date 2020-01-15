package stasis.client.ops.search

import java.nio.file.Path
import java.time.Instant

import stasis.client.model.FilesystemMetadata
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}

import scala.concurrent.Future
import scala.util.matching.Regex

trait Search {
  def search(query: Regex, until: Option[Instant]): Future[Search.Result]
}

object Search {
  final case class Result(
    definitions: Map[DatasetDefinition.Id, Option[DatasetDefinitionResult]]
  )

  final case class DatasetDefinitionResult(
    definitionInfo: String,
    entryId: DatasetEntry.Id,
    entryCreated: Instant,
    matches: Map[Path, FilesystemMetadata.FileState]
  )
}

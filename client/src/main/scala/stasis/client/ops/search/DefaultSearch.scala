package stasis.client.ops.search

import java.time.Instant

import stasis.client.api.clients.ServerApiEndpointClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

class DefaultSearch(
  api: ServerApiEndpointClient
)(implicit ec: ExecutionContext)
    extends Search {
  override def search(query: Regex, until: Option[Instant]): Future[Search.Result] = {
    val regex = query.pattern

    val metadataResult = for {
      definitions <- api.datasetDefinitions()
      entries <- Future
        .sequence(
          definitions.map { definition =>
            api.latestEntry(definition.id, until).map(entry => (definition, entry))
          }
        )
      metadata <- Future
        .sequence(
          entries.map {
            case (definition, Some(entry)) =>
              api.datasetMetadata(entry).map(metadata => (definition, Some((entry, metadata))))

            case (definition, None) =>
              Future.successful((definition, None))
          }
        )
    } yield {
      metadata
    }

    metadataResult.map { metadata =>
      val definitions = metadata.map {
        case (definition, Some((entry, metadata))) =>
          val matches = metadata.filesystem.files.filter {
            case (path, _) => regex.matcher(path.toAbsolutePath.toString).matches
          }

          val result = Search.DatasetDefinitionResult(
            definitionInfo = definition.info,
            entryId = entry.id,
            entryCreated = entry.created,
            matches = matches
          )

          if (matches.nonEmpty) {
            (definition.id, Some(result))
          } else {
            (definition.id, None)
          }

        case (definition, None) =>
          (definition.id, None)
      }

      Search.Result(definitions = definitions.toMap)
    }
  }
}

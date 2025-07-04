package stasis.client.api.http.routes

import java.time.Instant

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller

import stasis.client.api.Context
import stasis.client.ops.search.Search

class DatasetMetadata()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import DatasetMetadata._
  import stasis.client.api.http.Formats._

  def routes(): Route =
    concat(
      path(JavaUUID) { entryId =>
        get {
          onSuccess(context.api.datasetMetadata(entry = entryId)) { metadata =>
            log.debugN("API successfully retrieved metadata for entry [{}]", entryId)
            context.analytics.recordEvent(name = "get_dataset_metadata")

            consumeEntity & complete(metadata)
          }
        }
      },
      path("search") {
        get {
          parameters(
            "query".as[Search.Query],
            "until".as[Instant].?
          ) { case (query, until) =>
            onSuccess(context.search.search(query, until)) { result =>
              log.debugN(
                "API found [{}] matches for [{}] definitions with query [{} (original={})]",
                result.definitions.count(_._2.nonEmpty),
                result.definitions.size,
                query.pattern,
                query.original
              )
              context.analytics.recordEvent(name = "search_dataset_metadata")

              consumeEntity & complete(result)
            }
          }
        }
      }
    )
}

object DatasetMetadata {
  def apply()(implicit context: Context): DatasetMetadata =
    new DatasetMetadata()

  implicit val stringToSearchQuery: Unmarshaller[String, Search.Query] =
    Unmarshaller.strict(Search.Query.apply)
}

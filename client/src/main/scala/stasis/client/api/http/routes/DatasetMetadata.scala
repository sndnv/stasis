package stasis.client.api.http.routes

import java.time.Instant

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.client.api.http.Context

class DatasetMetadata()(implicit override val mat: Materializer, context: Context) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.client.api.http.Formats._
  import stasis.core.api.Matchers._

  def routes(): Route =
    concat(
      path(JavaUUID) { entryId =>
        get {
          onSuccess(context.api.datasetMetadata(entry = entryId)) { metadata =>
            log.debugN("API successfully retrieved metadata for entry [{}]", entryId)
            discardEntity & complete(metadata)
          }
        }
      },
      path("search") {
        get {
          parameters(
            "query".as[String],
            "until".as[Instant].?
          ) { case (query, until) =>
            onSuccess(context.search.search(query.r, until)) { result =>
              log.debugN(
                "API found [{}] matches for [{}] definitions with query [{}]",
                result.definitions.count(_._2.nonEmpty),
                result.definitions.size,
                query
              )

              discardEntity & complete(result)
            }
          }
        }
      }
    )
}

object DatasetMetadata {
  def apply()(implicit mat: Materializer, context: Context): DatasetMetadata =
    new DatasetMetadata()
}

package stasis.client.api.http.routes

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.client.api.http.Context

class DatasetEntries()(implicit override val mat: Materializer, context: Context) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Matchers._
  import stasis.shared.api.Formats._

  def routes(): Route =
    pathPrefix(JavaUUID) { definition =>
      concat(
        pathEndOrSingleSlash {
          get {
            onSuccess(context.api.datasetEntries(definition)) { entries =>
              log.debug("API successfully retrieved [{}] entries", entries.size)
              discardEntity & complete(entries)
            }
          }
        },
        path("latest") {
          get {
            parameter("until".as[Instant].?) { until =>
              onSuccess(context.api.latestEntry(definition, until)) {
                case Some(entry) =>
                  log.debug(
                    "API successfully retrieved latest entry [{}] for definition [{}]",
                    entry.id,
                    definition
                  )
                  discardEntity & complete(entry)

                case None =>
                  log.debug(
                    "API could not retrieve latest entry for definition [{}]; no entry found",
                    definition
                  )
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          }
        }
      )
    }
}
package stasis.client.api.http.routes

import java.time.Instant

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.client.api.http.Context

import scala.concurrent.Future

class DatasetEntries()(implicit context: Context) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Matchers._
  import stasis.shared.api.Formats._

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          extractExecutionContext { implicit ec =>
            val result = for {
              definitions <- context.api.datasetDefinitions()
              entries <- Future.sequence(definitions.map(definition => context.api.datasetEntries(definition.id)))
            } yield {
              entries.flatten
            }

            onSuccess(result) { entries =>
              log.debugN("API successfully retrieved [{}] entries for all definitions", entries.size)
              discardEntity & complete(entries)
            }
          }
        }
      },
      pathPrefix(JavaUUID) { definition =>
        concat(
          pathEndOrSingleSlash {
            get {
              onSuccess(context.api.datasetEntries(definition)) { entries =>
                log.debugN("API successfully retrieved [{}] entries", entries.size)
                discardEntity & complete(entries)
              }
            }
          },
          path("latest") {
            get {
              parameter("until".as[Instant].?) { until =>
                onSuccess(context.api.latestEntry(definition, until)) {
                  case Some(entry) =>
                    log.debugN(
                      "API successfully retrieved latest entry [{}] for definition [{}]",
                      entry.id,
                      definition
                    )
                    discardEntity & complete(entry)

                  case None =>
                    log.debugN(
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
    )
}

object DatasetEntries {
  def apply()(implicit context: Context): DatasetEntries =
    new DatasetEntries()
}

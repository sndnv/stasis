package stasis.client.api.http.routes

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.client.api.Context

class DatasetEntries()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
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
              consumeEntity & complete(entries)
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
                consumeEntity & complete(entries)
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
                    consumeEntity & complete(entry)

                  case None =>
                    log.debugN(
                      "API could not retrieve latest entry for definition [{}]; no entry found",
                      definition
                    )
                    consumeEntity & complete(StatusCodes.NotFound)
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

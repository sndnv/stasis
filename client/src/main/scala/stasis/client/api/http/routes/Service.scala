package stasis.client.api.http.routes

import java.time.Instant

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.client.api.Context
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import stasis.shared.api.responses.Ping

class Service()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  def routes(): Route =
    concat(
      path("ping") {
        get {
          val response = Ping()
          log.debug("Received ping request; responding with [{}]", response.id)
          consumeEntity & complete(response)
        }
      },
      pathPrefix("analytics") {
        concat(
          pathEndOrSingleSlash {
            get {
              onSuccess(context.analytics.state) { entry =>
                val result = Service.AnalyticsState(
                  entry = entry,
                  lastCached = context.analytics.persistence.map(_.lastCached),
                  lastTransmitted = context.analytics.persistence.map(_.lastTransmitted)
                )

                log.debug(
                  "Received analytics state request; responding with [{}] event(s) and [{}] failure(s)",
                  entry.events,
                  entry.failures
                )

                consumeEntity & complete(result)
              }
            }
          },
          path("send") {
            put {
              context.analytics.send()
              consumeEntity & complete(StatusCodes.Accepted)
            }
          }
        )
      },
      path("stop") {
        put {
          log.info("Received client termination request; stopping...")
          context.handlers.terminateService()
          consumeEntity & complete(StatusCodes.NoContent)
        }
      }
    )
}

object Service {
  def apply()(implicit context: Context): Service =
    new Service()

  final case class AnalyticsState(
    entry: AnalyticsEntry,
    lastCached: Option[Instant],
    lastTransmitted: Option[Instant]
  )

  import play.api.libs.json._

  import io.github.sndnv.layers.api.Formats._

  implicit val operationStartedFormat: Format[AnalyticsState] =
    Json.format[AnalyticsState]
}

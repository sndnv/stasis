package stasis.server.api.routes

import java.time.Instant

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import stasis.server.persistence.analytics.AnalyticsEntryStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateAnalyticsEntry
import stasis.shared.api.responses.CreatedAnalyticsEntry
import stasis.shared.api.responses.DeletedAnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry

class Analytics()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          post {
            entity(as[CreateAnalyticsEntry]) { createRequest =>
              resource[AnalyticsEntryStore.Manage.Self] { manage =>
                val entry = createRequest.toStoredAnalyticsEntry
                manage.create(entry).map { _ =>
                  log.debugN("User [{}] successfully created analytics entry [{}]", currentUser, entry.id)
                  complete(CreatedAnalyticsEntry(entry.id))
                }
              }
            }
          },
          get {
            resource[AnalyticsEntryStore.View.Service] { view =>
              view.list().map { entries =>
                log.debugN("User [{}] successfully retrieved [{}] analytics entries", currentUser, entries.size)
                discardEntity & complete(entries.map(Analytics.AnalyticsEntrySummary.fromEntry))
              }
            }
          }
        )
      },
      path(JavaUUID) { entryId =>
        concat(
          get {
            resource[AnalyticsEntryStore.View.Service] { view =>
              view.get(entryId).map {
                case Some(entry) =>
                  log.debugN("User [{}] successfully retrieved analytics entry [{}]", currentUser, entryId)
                  discardEntity & complete(entry)

                case None =>
                  log.warnN("User [{}] failed to retrieve analytics entry [{}]", currentUser, entryId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            resource[AnalyticsEntryStore.Manage.Service] { manage =>
              manage.delete(entryId).map { deleted =>
                if (deleted) {
                  log.debugN("User [{}] successfully deleted analytics entry [{}]", currentUser, entryId)
                } else {
                  log.warnN("User [{}] failed to delete analytics entry [{}]", currentUser, entryId)
                }

                discardEntity & complete(DeletedAnalyticsEntry(existing = deleted))
              }
            }
          }
        )
      }
    )
}

object Analytics {
  def apply()(implicit ctx: RoutesContext): Analytics = new Analytics()

  final case class AnalyticsEntrySummary(
    id: StoredAnalyticsEntry.Id,
    runtime: AnalyticsEntry.RuntimeInformation,
    events: Int,
    failures: Int,
    created: Instant,
    updated: Instant,
    received: Instant
  )

  object AnalyticsEntrySummary {
    def fromEntry(entry: StoredAnalyticsEntry): AnalyticsEntrySummary = AnalyticsEntrySummary(
      id = entry.id,
      runtime = entry.runtime,
      events = entry.events.size,
      failures = entry.failures.size,
      created = entry.created,
      updated = entry.updated,
      received = entry.received
    )
  }

  import play.api.libs.json._

  import io.github.sndnv.layers.api.Formats._

  implicit val analyticsEntrySummaryFormat: Format[AnalyticsEntrySummary] =
    Json.format[AnalyticsEntrySummary]
}

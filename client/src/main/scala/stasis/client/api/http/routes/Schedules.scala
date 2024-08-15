package stasis.client.api.http.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.client.api.Context

class Schedules()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.client.api.http.Formats._
  import stasis.shared.api.Formats._

  def routes(): Route =
    concat(
      pathPrefix("public") {
        concat(
          pathEndOrSingleSlash {
            get {
              onSuccess(context.api.publicSchedules()) { schedules =>
                log.debug("API successfully retrieved [{}] schedules", schedules.size)
                consumeEntity & complete(schedules)
              }
            }
          },
          path(JavaUUID) { scheduleId =>
            get {
              onSuccess(context.api.publicSchedule(schedule = scheduleId)) { schedule =>
                log.debug("API successfully retrieve schedule [{}]", scheduleId)
                consumeEntity & complete(schedule)
              }
            }
          }
        )
      },
      pathPrefix("configured") {
        concat(
          pathEndOrSingleSlash {
            get {
              onSuccess(context.scheduler.schedules) { schedules =>
                consumeEntity & complete(schedules)
              }
            }
          },
          path("refresh") {
            put {
              onSuccess(context.scheduler.refresh()) { _ =>
                consumeEntity & complete(StatusCodes.NoContent)
              }
            }
          }
        )
      }
    )
}

object Schedules {
  def apply()(implicit context: Context): Schedules =
    new Schedules()
}

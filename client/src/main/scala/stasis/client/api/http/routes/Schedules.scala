package stasis.client.api.http.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.client.api.http.Context

class Schedules()(implicit context: Context) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
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
                discardEntity & complete(schedules)
              }
            }
          },
          path(JavaUUID) { scheduleId =>
            get {
              onSuccess(context.api.publicSchedule(schedule = scheduleId)) { schedule =>
                log.debug("API successfully retrieve schedule [{}]", scheduleId)
                discardEntity & complete(schedule)
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
                discardEntity & complete(schedules)
              }
            }
          },
          path("refresh") {
            put {
              onSuccess(context.scheduler.refresh()) { _ =>
                discardEntity & complete(StatusCodes.NoContent)
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

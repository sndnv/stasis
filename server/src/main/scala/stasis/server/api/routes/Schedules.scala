package stasis.server.api.routes

import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.model.schedules.ScheduleStore
import stasis.shared.api.requests.{CreateSchedule, UpdateSchedule}
import stasis.shared.api.responses.{CreatedSchedule, DeletedSchedule}

object Schedules extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def apply()(implicit ctx: RoutesContext): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[ScheduleStore.View.Service] { view =>
              view.list().map { schedules =>
                log.info("User [{}] successfully retrieved [{}] schedules", ctx.user, schedules.size)
                complete(schedules.values)
              }
            }
          },
          post {
            entity(as[CreateSchedule]) { createRequest =>
              resource[ScheduleStore.Manage.Service] { manage =>
                val schedule = createRequest.toSchedule

                manage.create(schedule).map { _ =>
                  log.info("User [{}] successfully created schedule [{}]", ctx.user, schedule.id)
                  complete(CreatedSchedule(schedule.id))
                }
              }
            }
          }
        )
      },
      path(JavaUUID) { scheduleId =>
        concat(
          get {
            resource[ScheduleStore.View.Service] { view =>
              view.get(scheduleId).map {
                case Some(schedule) =>
                  log.info("User [{}] successfully retrieved schedule [{}]", ctx.user, scheduleId)
                  complete(schedule)

                case None =>
                  log.warning("User [{}] failed to retrieve schedule [{}]", ctx.user, scheduleId)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          put {
            entity(as[UpdateSchedule]) {
              updateRequest =>
                resources[ScheduleStore.View.Service, ScheduleStore.Manage.Service] {
                  (view, manage) =>
                    view.get(scheduleId).flatMap {
                      case Some(schedule) =>
                        manage.update(updateRequest.toUpdatedSchedule(schedule)).map { _ =>
                          log.info("User [{}] successfully updated schedule [{}]", ctx.user, scheduleId)
                          complete(StatusCodes.OK)
                        }

                      case None =>
                        log.warning("User [{}] failed to update missing schedule [{}]", ctx.user, scheduleId)
                        Future.successful(complete(StatusCodes.BadRequest))
                    }
                }
            }
          },
          delete {
            resource[ScheduleStore.Manage.Service] { manage =>
              manage.delete(scheduleId).map { deleted =>
                if (deleted) {
                  log.info("User [{}] successfully deleted schedule [{}]", ctx.user, scheduleId)
                } else {
                  log.warning("User [{}] failed to delete schedule [{}]", ctx.user, scheduleId)
                }

                complete(DeletedSchedule(existing = deleted))
              }
            }
          }
        )
      }
    )
}

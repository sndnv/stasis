package stasis.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.model.schedules.ScheduleStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.{CreateSchedule, UpdateSchedule}
import stasis.shared.api.responses.{CreatedSchedule, DeletedSchedule}

import scala.concurrent.Future

class Schedules()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[ScheduleStore.View.Service] { view =>
              view.list().map { schedules =>
                log.debug("User [{}] successfully retrieved [{}] schedules", currentUser, schedules.size)
                discardEntity & complete(schedules.values)
              }
            }
          },
          post {
            entity(as[CreateSchedule]) { createRequest =>
              resource[ScheduleStore.Manage.Service] { manage =>
                val schedule = createRequest.toSchedule

                manage.create(schedule).map { _ =>
                  log.debug("User [{}] successfully created schedule [{}]", currentUser, schedule.id)
                  complete(CreatedSchedule(schedule.id))
                }
              }
            }
          }
        )
      },
      pathPrefix("public") {
        concat(
          pathEndOrSingleSlash {
            get {
              resource[ScheduleStore.View.Public] { view =>
                view.list().map { schedules =>
                  log.debug("User [{}] successfully retrieved [{}] public schedules", currentUser, schedules.size)
                  discardEntity & complete(schedules.values)
                }
              }
            }
          },
          path(JavaUUID) { scheduleId =>
            get {
              resource[ScheduleStore.View.Public] { view =>
                view.get(scheduleId).map {
                  case Some(schedule) =>
                    log.debug("User [{}] successfully retrieved public schedule [{}]", currentUser, scheduleId)
                    discardEntity & complete(schedule)

                  case None =>
                    log.warning("User [{}] failed to retrieve public schedule [{}]", currentUser, scheduleId)
                    discardEntity & complete(StatusCodes.NotFound)
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
                  log.debug("User [{}] successfully retrieved schedule [{}]", currentUser, scheduleId)
                  discardEntity & complete(schedule)

                case None =>
                  log.warning("User [{}] failed to retrieve schedule [{}]", currentUser, scheduleId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          put {
            entity(as[UpdateSchedule]) {
              updateRequest =>
                resources[ScheduleStore.View.Service, ScheduleStore.Manage.Service] { (view, manage) =>
                  view.get(scheduleId).flatMap {
                    case Some(schedule) =>
                      manage.update(updateRequest.toUpdatedSchedule(schedule)).map { _ =>
                        log.debug("User [{}] successfully updated schedule [{}]", currentUser, scheduleId)
                        complete(StatusCodes.OK)
                      }

                    case None =>
                      log.warning("User [{}] failed to update missing schedule [{}]", currentUser, scheduleId)
                      Future.successful(complete(StatusCodes.BadRequest))
                  }
                }
            }
          },
          delete {
            resource[ScheduleStore.Manage.Service] { manage =>
              manage.delete(scheduleId).map { deleted =>
                if (deleted) {
                  log.debug("User [{}] successfully deleted schedule [{}]", currentUser, scheduleId)
                } else {
                  log.warning("User [{}] failed to delete schedule [{}]", currentUser, scheduleId)
                }

                discardEntity & complete(DeletedSchedule(existing = deleted))
              }
            }
          }
        )
      }
    )
}

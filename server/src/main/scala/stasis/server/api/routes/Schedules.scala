package stasis.server.api.routes

import scala.concurrent.Future

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.server.persistence.schedules.ScheduleStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateSchedule
import stasis.shared.api.requests.UpdateSchedule
import stasis.shared.api.responses.CreatedSchedule
import stasis.shared.api.responses.DeletedSchedule

class Schedules()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[ScheduleStore.View.Service] { view =>
              view.list().map { schedules =>
                log.debugN("User [{}] successfully retrieved [{}] schedules", currentUser, schedules.size)
                discardEntity & complete(schedules)
              }
            }
          },
          post {
            entity(as[CreateSchedule]) { createRequest =>
              resource[ScheduleStore.Manage.Service] { manage =>
                val schedule = createRequest.toSchedule

                manage.put(schedule).map { _ =>
                  log.debugN("User [{}] successfully created schedule [{}]", currentUser, schedule.id)
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
                  log.debugN("User [{}] successfully retrieved [{}] public schedules", currentUser, schedules.size)
                  discardEntity & complete(schedules)
                }
              }
            }
          },
          path(JavaUUID) { scheduleId =>
            get {
              resource[ScheduleStore.View.Public] { view =>
                view.get(scheduleId).map {
                  case Some(schedule) =>
                    log.debugN("User [{}] successfully retrieved public schedule [{}]", currentUser, scheduleId)
                    discardEntity & complete(schedule)

                  case None =>
                    log.warnN("User [{}] failed to retrieve public schedule [{}]", currentUser, scheduleId)
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
                  log.debugN("User [{}] successfully retrieved schedule [{}]", currentUser, scheduleId)
                  discardEntity & complete(schedule)

                case None =>
                  log.warnN("User [{}] failed to retrieve schedule [{}]", currentUser, scheduleId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          put {
            entity(as[UpdateSchedule]) { updateRequest =>
              resources[ScheduleStore.View.Service, ScheduleStore.Manage.Service] { (view, manage) =>
                view.get(scheduleId).flatMap {
                  case Some(schedule) =>
                    manage.put(updateRequest.toUpdatedSchedule(schedule)).map { _ =>
                      log.debugN("User [{}] successfully updated schedule [{}]", currentUser, scheduleId)
                      complete(StatusCodes.OK)
                    }

                  case None =>
                    log.warnN("User [{}] failed to update missing schedule [{}]", currentUser, scheduleId)
                    Future.successful(complete(StatusCodes.BadRequest))
                }
              }
            }
          },
          delete {
            resource[ScheduleStore.Manage.Service] { manage =>
              manage.delete(scheduleId).map { deleted =>
                if (deleted) {
                  log.debugN("User [{}] successfully deleted schedule [{}]", currentUser, scheduleId)
                } else {
                  log.warnN("User [{}] failed to delete schedule [{}]", currentUser, scheduleId)
                }

                discardEntity & complete(DeletedSchedule(existing = deleted))
              }
            }
          }
        )
      }
    )
}

object Schedules {
  def apply()(implicit ctx: RoutesContext): Schedules = new Schedules()
}

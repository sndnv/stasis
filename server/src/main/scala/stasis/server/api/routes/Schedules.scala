package stasis.server.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests.{CreateSchedule, UpdateSchedule}
import stasis.server.api.responses.{CreatedSchedule, DeletedSchedule}
import stasis.server.model.schedules.ScheduleStore
import stasis.server.model.users.User
import stasis.server.security.ResourceProvider

import scala.concurrent.ExecutionContext

object Schedules {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  def apply(
    resourceProvider: ResourceProvider,
    currentUser: User.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(
              resourceProvider.provide[ScheduleStore.View.Service](currentUser).flatMap(_.list())
            ) { schedules =>
              log.info("User [{}] successfully retrieved [{}] schedules", currentUser, schedules.size)
              complete(schedules.values)
            }
          },
          post {
            entity(as[CreateSchedule]) { createRequest =>
              val schedule = createRequest.toSchedule
              onSuccess(
                resourceProvider
                  .provide[ScheduleStore.Manage.Service](currentUser)
                  .flatMap(_.create(schedule))
              ) { _ =>
                log.info("User [{}] successfully created schedule [{}]", currentUser, schedule.id)
                complete(CreatedSchedule(schedule.id))
              }
            }
          }
        )
      },
      path(JavaUUID) { scheduleId =>
        concat(
          get {
            onSuccess(
              resourceProvider.provide[ScheduleStore.View.Service](currentUser).flatMap(_.get(scheduleId))
            ) {
              case Some(schedule) =>
                log.info("User [{}] successfully retrieved schedule [{}]", currentUser, scheduleId)
                complete(schedule)

              case None =>
                log.warning("User [{}] failed to retrieve schedule [{}]", currentUser, scheduleId)
                complete(StatusCodes.NotFound)
            }
          },
          put {
            entity(as[UpdateSchedule]) {
              updateRequest =>
                onSuccess(
                  resourceProvider.provide[ScheduleStore.View.Service](currentUser).flatMap(_.get(scheduleId))
                ) {
                  case Some(schedule) =>
                    onSuccess(
                      resourceProvider
                        .provide[ScheduleStore.Manage.Service](currentUser)
                        .flatMap(_.update(updateRequest.toUpdatedSchedule(schedule)))
                    ) { _ =>
                      log.info("User [{}] successfully updated schedule [{}]", currentUser, scheduleId)
                      complete(StatusCodes.OK)
                    }

                  case None =>
                    log.warning("User [{}] failed to update missing schedule [{}]", currentUser, scheduleId)
                    complete(StatusCodes.BadRequest)
                }
            }
          },
          delete {
            onSuccess(
              resourceProvider.provide[ScheduleStore.Manage.Service](currentUser).flatMap(_.delete(scheduleId))
            ) { deleted =>
              if (deleted) {
                log.info("User [{}] successfully deleted schedule [{}]", currentUser, scheduleId)
              } else {
                log.warning("User [{}] failed to delete schedule [{}]", currentUser, scheduleId)
              }

              complete(DeletedSchedule(existing = deleted))
            }
          }
        )
      }
    )
}

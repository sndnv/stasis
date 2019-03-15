package stasis.server.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests._
import stasis.server.api.responses.{CreatedDevice, DeletedDevice}
import stasis.server.model.devices.{Device, DeviceStore}
import stasis.server.model.users.{User, UserStore}
import stasis.server.security.ResourceProvider

import scala.concurrent.{ExecutionContext, Future}

object Devices {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  private def updatePrivileged(
    resourceProvider: ResourceProvider,
    currentUser: User.Id,
    updateRequest: UpdateDevice,
    deviceId: Device.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    onSuccess(
      for {
        deviceOpt <- resourceProvider.provide[DeviceStore.View.Privileged](currentUser).flatMap(_.get(deviceId))
        ownerOpt <- deviceOpt match {
          case Some(device) =>
            resourceProvider.provide[UserStore.View.Privileged](currentUser).flatMap(_.get(device.owner))
          case None =>
            Future.successful(None)
        }
      } yield {
        (deviceOpt, ownerOpt)
      }
    ) {
      case (Some(device), Some(owner)) =>
        onSuccess(
          resourceProvider
            .provide[DeviceStore.Manage.Privileged](currentUser)
            .flatMap(_.update(updateRequest.toUpdatedDevice(device, owner)))
        ) { _ =>
          log.info("User [{}] successfully updated device [{}]", currentUser, deviceId)
          complete(StatusCodes.OK)
        }

      case (deviceOpt, _) =>
        log.warning(
          "User [{}] failed to update device [{}]",
          currentUser,
          deviceOpt.map(_.id)
        )
        complete(StatusCodes.BadRequest)
    }

  private def updateOwn(
    resourceProvider: ResourceProvider,
    currentUser: User.Id,
    updateRequest: UpdateDevice,
    deviceId: Device.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    onSuccess(
      for {
        deviceOpt <- resourceProvider
          .provide[DeviceStore.View.Self](currentUser)
          .flatMap(_.get(currentUser, deviceId))
        ownerOpt <- deviceOpt match {
          case Some(_) =>
            resourceProvider.provide[UserStore.View.Self](currentUser).flatMap(_.get(currentUser))
          case None =>
            Future.successful(None)
        }
      } yield {
        (deviceOpt, ownerOpt)
      }
    ) {
      case (Some(device), Some(owner)) =>
        onSuccess(
          resourceProvider
            .provide[DeviceStore.Manage.Self](currentUser)
            .flatMap(_.update(currentUser, updateRequest.toUpdatedDevice(device, owner)))
        ) { _ =>
          log.info("User [{}] successfully updated device [{}]", currentUser, deviceId)
          complete(StatusCodes.OK)
        }

      case (deviceOpt, _) =>
        log.warning(
          "User [{}] failed to update device [{}]",
          currentUser,
          deviceOpt.map(_.id)
        )
        complete(StatusCodes.BadRequest)
    }

  def apply(
    resourceProvider: ResourceProvider,
    currentUser: User.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(
              resourceProvider.provide[DeviceStore.View.Privileged](currentUser).flatMap(_.list())
            ) { devices =>
              log.info("User [{}] successfully retrieved [{}] devices", currentUser, devices.size)
              complete(devices.values)
            }
          },
          post {
            entity(as[CreateDevicePrivileged]) {
              createRequest =>
                onSuccess(
                  resourceProvider.provide[UserStore.View.Privileged](currentUser).flatMap(_.get(createRequest.owner))
                ) {
                  case Some(owner) =>
                    val device = createRequest.toDevice(owner)
                    onSuccess(
                      resourceProvider
                        .provide[DeviceStore.Manage.Privileged](currentUser)
                        .flatMap(_.create(device))
                    ) { _ =>
                      log.info("User [{}] successfully created device [{}]", currentUser, device.id)
                      complete(CreatedDevice(device.id))
                    }

                  case None =>
                    log.warning(
                      "User [{}] failed to retrieve device owner data for user [{}]",
                      currentUser,
                      createRequest.owner
                    )
                    complete(StatusCodes.BadRequest)
                }
            }
          }
        )
      },
      pathPrefix(JavaUUID) { deviceId =>
        concat(
          pathEndOrSingleSlash {
            concat(
              get {
                onSuccess(
                  resourceProvider.provide[DeviceStore.View.Privileged](currentUser).flatMap(_.get(deviceId))
                ) {
                  case Some(device) =>
                    log.info("User [{}] successfully retrieved device [{}]", currentUser, deviceId)
                    complete(device)

                  case None =>
                    log.warning("User [{}] failed to retrieve device [{}]", currentUser, deviceId)
                    complete(StatusCodes.NotFound)
                }
              },
              delete {
                onSuccess(
                  resourceProvider.provide[DeviceStore.Manage.Privileged](currentUser).flatMap(_.delete(deviceId))
                ) { deleted =>
                  if (deleted) {
                    log.info("User [{}] successfully deleted device [{}]", currentUser, deviceId)
                  } else {
                    log.warning("User [{}] failed to delete device [{}]", currentUser, deviceId)
                  }

                  complete(DeletedDevice(existing = deleted))
                }
              }
            )
          },
          path("limits") {
            put {
              entity(as[UpdateDeviceLimits]) { updateRequest =>
                updatePrivileged(resourceProvider, currentUser, updateRequest, deviceId)
              }
            }
          },
          path("state") {
            put {
              entity(as[UpdateDeviceState]) { updateRequest =>
                updatePrivileged(resourceProvider, currentUser, updateRequest, deviceId)
              }
            }
          }
        )
      },
      pathPrefix("own") {
        concat(
          pathEndOrSingleSlash {
            concat(
              get {
                onSuccess(
                  resourceProvider.provide[DeviceStore.View.Self](currentUser).flatMap(_.list(currentUser))
                ) { devices =>
                  log.info("User [{}] successfully retrieved [{}] devices", currentUser, devices.size)
                  complete(devices.values)
                }
              },
              post {
                entity(as[CreateDeviceOwn]) {
                  createRequest =>
                    onSuccess(
                      resourceProvider.provide[UserStore.View.Self](currentUser).flatMap(_.get(currentUser))
                    ) {
                      case Some(owner) =>
                        val device = createRequest.toDevice(owner)
                        onSuccess(
                          resourceProvider
                            .provide[DeviceStore.Manage.Self](currentUser)
                            .flatMap(_.create(currentUser, device))
                        ) { _ =>
                          log.info("User [{}] successfully created device [{}]", currentUser, device.id)
                          complete(CreatedDevice(device.id))
                        }

                      case None =>
                        log.warning(
                          "User [{}] failed to retrieve device owner data for user [{}]",
                          currentUser,
                          currentUser
                        )
                        complete(StatusCodes.BadRequest)
                    }
                }
              }
            )
          },
          pathPrefix(JavaUUID) {
            deviceId =>
              concat(
                pathEndOrSingleSlash {
                  concat(
                    get {
                      onSuccess(
                        resourceProvider
                          .provide[DeviceStore.View.Self](currentUser)
                          .flatMap(_.get(currentUser, deviceId))
                      ) {
                        case Some(device) =>
                          log.info("User [{}] successfully retrieved device [{}]", currentUser, deviceId)
                          complete(device)

                        case None =>
                          log.warning("User [{}] failed to retrieve device [{}]", currentUser, deviceId)
                          complete(StatusCodes.NotFound)
                      }
                    },
                    delete {
                      onSuccess(
                        resourceProvider
                          .provide[DeviceStore.Manage.Self](currentUser)
                          .flatMap(_.delete(currentUser, deviceId))
                      ) { deleted =>
                        if (deleted) {
                          log.info("User [{}] successfully deleted device [{}]", currentUser, deviceId)
                        } else {
                          log.warning("User [{}] failed to delete device [{}]", currentUser, deviceId)
                        }

                        complete(DeletedDevice(existing = deleted))
                      }
                    }
                  )
                },
                path("limits") {
                  put {
                    entity(as[UpdateDeviceLimits]) { updateRequest =>
                      updateOwn(resourceProvider, currentUser, updateRequest, deviceId)
                    }
                  }
                },
                path("state") {
                  put {
                    entity(as[UpdateDeviceState]) { updateRequest =>
                      updateOwn(resourceProvider, currentUser, updateRequest, deviceId)
                    }
                  }
                }
              )
          }
        )
      }
    )
}

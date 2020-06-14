package stasis.server.api.routes

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.model.devices.DeviceStore
import stasis.server.model.users.UserStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests._
import stasis.shared.api.responses.{CreatedDevice, DeletedDevice}
import stasis.shared.model.devices.Device

import scala.concurrent.Future

class Devices()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[DeviceStore.View.Privileged] { view =>
              view.list().map { devices =>
                log.debugN("User [{}] successfully retrieved [{}] devices", currentUser, devices.size)
                discardEntity & complete(devices.values)
              }
            }
          },
          post {
            entity(as[CreateDevicePrivileged]) {
              createRequest =>
                resources[UserStore.View.Privileged, DeviceStore.Manage.Privileged] {
                  (userView, deviceManage) =>
                    userView.get(createRequest.owner).flatMap {
                      case Some(owner) =>
                        val device = createRequest.toDevice(owner)
                        deviceManage.create(device).map { _ =>
                          log.debugN("User [{}] successfully created device [{}]", currentUser, device.id)
                          complete(CreatedDevice(device.id))
                        }

                      case None =>
                        log.warnN(
                          "User [{}] failed to retrieve device owner data for user [{}]",
                          currentUser,
                          createRequest.owner
                        )
                        Future.successful(complete(StatusCodes.BadRequest))
                    }
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
                resource[DeviceStore.View.Privileged] { view =>
                  view.get(deviceId).map {
                    case Some(device) =>
                      log.debugN("User [{}] successfully retrieved device [{}]", currentUser, deviceId)
                      discardEntity & complete(device)

                    case None =>
                      log.warnN("User [{}] failed to retrieve device [{}]", currentUser, deviceId)
                      discardEntity & complete(StatusCodes.NotFound)
                  }
                }
              },
              delete {
                resource[DeviceStore.Manage.Privileged] { manage =>
                  manage.delete(deviceId).map { deleted =>
                    if (deleted) {
                      log.debugN("User [{}] successfully deleted device [{}]", currentUser, deviceId)
                    } else {
                      log.warnN("User [{}] failed to delete device [{}]", currentUser, deviceId)
                    }

                    discardEntity & complete(DeletedDevice(existing = deleted))
                  }
                }

              }
            )
          },
          path("limits") {
            put {
              entity(as[UpdateDeviceLimits]) { updateRequest =>
                updatePrivileged(updateRequest, deviceId)
              }
            }
          },
          path("state") {
            put {
              entity(as[UpdateDeviceState]) { updateRequest =>
                updatePrivileged(updateRequest, deviceId)
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
                resource[DeviceStore.View.Self] { view =>
                  view.list(currentUser).map { devices =>
                    log.debugN("User [{}] successfully retrieved [{}] devices", currentUser, devices.size)
                    discardEntity & complete(devices.values)
                  }
                }
              },
              post {
                entity(as[CreateDeviceOwn]) {
                  createRequest =>
                    resources[UserStore.View.Self, DeviceStore.Manage.Self] {
                      (userView, deviceManage) =>
                        userView.get(currentUser).flatMap {
                          case Some(owner) =>
                            val device = createRequest.toDevice(owner)
                            deviceManage.create(currentUser, device).map { _ =>
                              log.debugN("User [{}] successfully created device [{}]", currentUser, device.id)
                              complete(CreatedDevice(device.id))
                            }

                          case None =>
                            log.warnN("User [{}] failed to retrieve own data", currentUser)
                            Future.successful(complete(StatusCodes.BadRequest))
                        }
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
                      resource[DeviceStore.View.Self] { view =>
                        view.get(currentUser, deviceId).map {
                          case Some(device) =>
                            log.debugN("User [{}] successfully retrieved device [{}]", currentUser, deviceId)
                            discardEntity & complete(device)

                          case None =>
                            log.warnN("User [{}] failed to retrieve device [{}]", currentUser, deviceId)
                            discardEntity & complete(StatusCodes.NotFound)
                        }
                      }
                    },
                    delete {
                      resource[DeviceStore.Manage.Self] { manage =>
                        manage.delete(currentUser, deviceId).map { deleted =>
                          if (deleted) {
                            log.debugN("User [{}] successfully deleted device [{}]", currentUser, deviceId)
                          } else {
                            log.warnN("User [{}] failed to delete device [{}]", currentUser, deviceId)
                          }

                          discardEntity & complete(DeletedDevice(existing = deleted))
                        }
                      }
                    }
                  )
                },
                path("limits") {
                  put {
                    entity(as[UpdateDeviceLimits]) { updateRequest =>
                      updateOwn(updateRequest, deviceId)
                    }
                  }
                },
                path("state") {
                  put {
                    entity(as[UpdateDeviceState]) { updateRequest =>
                      updateOwn(updateRequest, deviceId)
                    }
                  }
                }
              )
          }
        )
      }
    )

  private def updatePrivileged(
    updateRequest: UpdateDevice,
    deviceId: Device.Id
  )(implicit ctx: RoutesContext, currentUser: CurrentUser): Route =
    resources[DeviceStore.View.Privileged, UserStore.View.Privileged, DeviceStore.Manage.Privileged] {
      (deviceView, userView, deviceManage) =>
        deviceView.get(deviceId).flatMap {
          case Some(device) =>
            userView.get(device.owner).flatMap {
              case Some(owner) =>
                deviceManage.update(updateRequest.toUpdatedDevice(device, owner)).map { _ =>
                  log.debugN("User [{}] successfully updated device [{}]", currentUser, deviceId)
                  complete(StatusCodes.OK)
                }

              case None =>
                log.warnN(
                  "User [{}] failed to update device [{}]; device owner [{}] not found",
                  currentUser,
                  deviceId,
                  device.owner
                )
                Future.successful(complete(StatusCodes.BadRequest))
            }

          case None =>
            log.warnN(
              "User [{}] failed to update device [{}]; device not found",
              currentUser,
              deviceId
            )
            Future.successful(complete(StatusCodes.BadRequest))
        }
    }

  private def updateOwn(
    updateRequest: UpdateDevice,
    deviceId: Device.Id
  )(implicit ctx: RoutesContext, currentUser: CurrentUser): Route =
    resources[DeviceStore.View.Self, UserStore.View.Self, DeviceStore.Manage.Self] { (deviceView, userView, deviceManage) =>
      deviceView.get(currentUser, deviceId).flatMap {
        case Some(device) =>
          userView.get(currentUser).flatMap {
            case Some(owner) =>
              deviceManage.update(currentUser, updateRequest.toUpdatedDevice(device, owner)).map { _ =>
                log.debugN("User [{}] successfully updated device [{}]", currentUser, deviceId)
                complete(StatusCodes.OK)
              }

            case None =>
              log.warnN(
                "User [{}] failed to update device [{}]; device owner [{}] not found",
                currentUser,
                deviceId,
                device.owner
              )
              Future.successful(complete(StatusCodes.BadRequest))
          }

        case None =>
          log.warnN(
            "User [{}] failed to update device [{}]; device not found",
            currentUser,
            deviceId
          )
          Future.successful(complete(StatusCodes.BadRequest))
      }
    }
}

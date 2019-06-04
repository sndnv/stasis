package stasis.server.api.routes

import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.model.devices.DeviceStore
import stasis.server.model.users.UserStore
import stasis.shared.api.requests._
import stasis.shared.api.responses.{CreatedDevice, DeletedDevice}
import stasis.shared.model.devices.Device

object Devices extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  private def updatePrivileged(
    updateRequest: UpdateDevice,
    deviceId: Device.Id
  )(implicit ctx: RoutesContext): Route =
    resources[DeviceStore.View.Privileged, UserStore.View.Privileged, DeviceStore.Manage.Privileged] {
      (deviceView, userView, deviceManage) =>
        deviceView.get(deviceId).flatMap {
          case Some(device) =>
            userView.get(device.owner).flatMap {
              case Some(owner) =>
                deviceManage.update(updateRequest.toUpdatedDevice(device, owner)).map { _ =>
                  log.info("User [{}] successfully updated device [{}]", ctx.user, deviceId)
                  complete(StatusCodes.OK)
                }

              case None =>
                log.warning(
                  "User [{}] failed to update device [{}]; device owner [{}] not found",
                  ctx.user,
                  deviceId,
                  device.owner
                )
                Future.successful(complete(StatusCodes.BadRequest))
            }

          case None =>
            log.warning(
              "User [{}] failed to update device [{}]; device not found",
              ctx.user,
              deviceId
            )
            Future.successful(complete(StatusCodes.BadRequest))
        }
    }

  private def updateOwn(
    updateRequest: UpdateDevice,
    deviceId: Device.Id
  )(implicit ctx: RoutesContext): Route =
    resources[DeviceStore.View.Self, UserStore.View.Self, DeviceStore.Manage.Self] {
      (deviceView, userView, deviceManage) =>
        deviceView.get(ctx.user, deviceId).flatMap {
          case Some(device) =>
            userView.get(ctx.user).flatMap {
              case Some(owner) =>
                deviceManage.update(ctx.user, updateRequest.toUpdatedDevice(device, owner)).map { _ =>
                  log.info("User [{}] successfully updated device [{}]", ctx.user, deviceId)
                  complete(StatusCodes.OK)
                }

              case None =>
                log.warning(
                  "User [{}] failed to update device [{}]; device owner [{}] not found",
                  ctx.user,
                  deviceId,
                  device.owner
                )
                Future.successful(complete(StatusCodes.BadRequest))
            }

          case None =>
            log.warning(
              "User [{}] failed to update device [{}]; device not found",
              ctx.user,
              deviceId
            )
            Future.successful(complete(StatusCodes.BadRequest))
        }
    }

  def apply()(implicit ctx: RoutesContext): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[DeviceStore.View.Privileged] { view =>
              view.list().map { devices =>
                log.info("User [{}] successfully retrieved [{}] devices", ctx.user, devices.size)
                complete(devices.values)
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
                          log.info("User [{}] successfully created device [{}]", ctx.user, device.id)
                          complete(CreatedDevice(device.id))
                        }

                      case None =>
                        log.warning(
                          "User [{}] failed to retrieve device owner data for user [{}]",
                          ctx.user,
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
                      log.info("User [{}] successfully retrieved device [{}]", ctx.user, deviceId)
                      complete(device)

                    case None =>
                      log.warning("User [{}] failed to retrieve device [{}]", ctx.user, deviceId)
                      complete(StatusCodes.NotFound)
                  }
                }
              },
              delete {
                resource[DeviceStore.Manage.Privileged] { manage =>
                  manage.delete(deviceId).map { deleted =>
                    if (deleted) {
                      log.info("User [{}] successfully deleted device [{}]", ctx.user, deviceId)
                    } else {
                      log.warning("User [{}] failed to delete device [{}]", ctx.user, deviceId)
                    }

                    complete(DeletedDevice(existing = deleted))
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
                  view.list(ctx.user).map { devices =>
                    log.info("User [{}] successfully retrieved [{}] devices", ctx.user, devices.size)
                    complete(devices.values)
                  }
                }
              },
              post {
                entity(as[CreateDeviceOwn]) {
                  createRequest =>
                    resources[UserStore.View.Self, DeviceStore.Manage.Self] {
                      (userView, deviceManage) =>
                        userView.get(ctx.user).flatMap {
                          case Some(owner) =>
                            val device = createRequest.toDevice(owner)
                            deviceManage.create(ctx.user, device).map { _ =>
                              log.info("User [{}] successfully created device [{}]", ctx.user, device.id)
                              complete(CreatedDevice(device.id))
                            }

                          case None =>
                            log.warning("User [{}] failed to retrieve own data", ctx.user)
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
                        view.get(ctx.user, deviceId).map {
                          case Some(device) =>
                            log.info("User [{}] successfully retrieved device [{}]", ctx.user, deviceId)
                            complete(device)

                          case None =>
                            log.warning("User [{}] failed to retrieve device [{}]", ctx.user, deviceId)
                            complete(StatusCodes.NotFound)
                        }
                      }
                    },
                    delete {
                      resource[DeviceStore.Manage.Self] { manage =>
                        manage.delete(ctx.user, deviceId).map { deleted =>
                          if (deleted) {
                            log.info("User [{}] successfully deleted device [{}]", ctx.user, deviceId)
                          } else {
                            log.warning("User [{}] failed to delete device [{}]", ctx.user, deviceId)
                          }

                          complete(DeletedDevice(existing = deleted))
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
}

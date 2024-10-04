package stasis.server.api.routes

import scala.concurrent.Future

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.server.model.devices.DeviceKeyStore
import stasis.server.model.devices.DeviceStore
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.model.users.UserStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.UpdateDeviceKey._
import stasis.shared.api.requests._
import stasis.shared.api.responses.CreatedDevice
import stasis.shared.api.responses.DeletedDevice
import stasis.shared.api.responses.DeletedDeviceKey
import stasis.shared.model.devices.Device

class Devices()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

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
            entity(as[CreateDevicePrivileged]) { createRequest =>
              resources[UserStore.View.Privileged, DeviceStore.Manage.Privileged] { (userView, deviceManage) =>
                userView.get(createRequest.owner).flatMap {
                  case Some(owner) =>
                    val device = createRequest.toDevice(owner)
                    deviceManage.create(device).map { _ =>
                      log.debugN(
                        "User [{}] successfully created device [{}] with node [{}]",
                        currentUser,
                        device.id,
                        device.node
                      )
                      complete(CreatedDevice(device.id, device.node))
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
          },
          path("key") {
            concat(
              get {
                resource[DeviceKeyStore.View.Privileged] { view =>
                  view.get(deviceId).map {
                    case Some(key) =>
                      log.debugN("User [{}] successfully retrieved key for device [{}]", currentUser, deviceId)
                      discardEntity & complete(key)

                    case None =>
                      log.warnN("User [{}] failed to retrieve key for device [{}]", currentUser, deviceId)
                      discardEntity & complete(StatusCodes.NotFound)
                  }
                }
              },
              delete {
                resource[DeviceKeyStore.Manage.Privileged] { manage =>
                  manage.delete(deviceId).map { deleted =>
                    if (deleted) {
                      log.debugN("User [{}] successfully deleted key for device [{}]", currentUser, deviceId)
                    } else {
                      log.warnN("User [{}] failed to delete key for device [{}]", currentUser, deviceId)
                    }

                    discardEntity & complete(DeletedDeviceKey(existing = deleted))
                  }
                }
              }
            )
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
                entity(as[CreateDeviceOwn]) { createRequest =>
                  resources[UserStore.View.Self, DeviceStore.Manage.Self, ServerNodeStore.Manage.Self] {
                    (userView, deviceManage, nodeManage) =>
                      userView.get(currentUser).flatMap {
                        case Some(owner) =>
                          val (device, node) = createRequest.toDeviceAndNode(owner)

                          for {
                            _ <- deviceManage.create(currentUser, device)
                            _ <- nodeManage.create(currentUser, device, node)
                          } yield {
                            log.debugN(
                              "User [{}] successfully created device [{}] with node [{}]",
                              currentUser,
                              device.id,
                              device.node
                            )
                            complete(CreatedDevice(device.id, device.node))
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
          pathPrefix(JavaUUID) { deviceId =>
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
              },
              path("key") {
                concat(
                  head {
                    resources[DeviceStore.View.Self, DeviceKeyStore.View.Self] { (deviceView, keyView) =>
                      deviceView.list(currentUser).flatMap { ownDevices =>
                        keyView.exists(ownDevices.values.map(_.id).toSeq, deviceId).map {
                          case true =>
                            log.debugN("User [{}] successfully retrieved key information for device [{}]", currentUser, deviceId)
                            val entity = HttpEntity(ContentTypes.`application/octet-stream`, Source.empty)
                            discardEntity & complete(entity)

                          case false =>
                            log.debugN("User [{}] failed to retrieve key information for device [{}]", currentUser, deviceId)
                            discardEntity & complete(StatusCodes.NotFound)
                        }
                      }
                    }
                  },
                  get {
                    resources[DeviceStore.View.Self, DeviceKeyStore.View.Self] { (deviceView, keyView) =>
                      deviceView.list(currentUser).flatMap { ownDevices =>
                        keyView.get(ownDevices.values.map(_.id).toSeq, deviceId).map {
                          case Some(key) =>
                            log.debugN("User [{}] successfully retrieved key for device [{}]", currentUser, deviceId)
                            val entity = HttpEntity(ContentTypes.`application/octet-stream`, Source.single(key.value))
                            discardEntity & complete(entity)

                          case None =>
                            log.warnN("User [{}] failed to retrieve key for device [{}]", currentUser, deviceId)
                            discardEntity & complete(StatusCodes.NotFound)
                        }
                      }
                    }
                  },
                  put {
                    requestEntityPresent {
                      extractDataBytes { requestStream =>
                        extractMaterializer { implicit mat =>
                          resources[DeviceStore.View.Self, UserStore.View.Self, DeviceKeyStore.Manage.Self] {
                            (deviceView, userView, keyManage) =>
                              deviceView.list(currentUser).map(devices => (devices.values, devices.get(deviceId))).flatMap {
                                case (ownDevices, Some(device)) =>
                                  userView.get(currentUser).flatMap {
                                    case Some(owner) =>
                                      for {
                                        keyBytes <- requestStream.runFold(ByteString.empty)(_ concat _)
                                        _ <- keyManage.put(ownDevices.map(_.id).toSeq, keyBytes.toDeviceKey(device, owner))
                                      } yield {
                                        log.debugN("User [{}] successfully updated key for device [{}]", currentUser, deviceId)
                                        complete(StatusCodes.OK)
                                      }

                                    case None =>
                                      log.warnN(
                                        "User [{}] failed to update key for device [{}]; device owner [{}] not found",
                                        currentUser,
                                        deviceId,
                                        device.owner
                                      )
                                      Future.successful(complete(StatusCodes.BadRequest))
                                  }

                                case _ =>
                                  log.warnN(
                                    "User [{}] failed to update key for device [{}]; device not found",
                                    currentUser,
                                    deviceId
                                  )
                                  Future.successful(complete(StatusCodes.BadRequest))
                              }
                          }
                        }
                      }
                    }
                  },
                  delete {
                    resources[DeviceStore.View.Self, DeviceKeyStore.Manage.Self] { (deviceView, keyManage) =>
                      deviceView.list(currentUser).flatMap { ownDevices =>
                        keyManage.delete(ownDevices.values.map(_.id).toSeq, deviceId).map { deleted =>
                          if (deleted) {
                            log.debugN("User [{}] successfully deleted key for device [{}]", currentUser, deviceId)
                          } else {
                            log.warnN("User [{}] failed to delete key for device [{}]", currentUser, deviceId)
                          }

                          discardEntity & complete(DeletedDeviceKey(existing = deleted))
                        }
                      }
                    }
                  }
                )
              }
            )
          },
          path("keys") {
            resources[DeviceStore.View.Self, DeviceKeyStore.View.Self] { (deviceView, keyView) =>
              deviceView.list(currentUser).flatMap { ownDevices =>
                keyView.list(ownDevices.values.map(_.id).toSeq).map { keys =>
                  log.debugN("User [{}] successfully retrieved [{}] device keys", currentUser, keys.size)
                  discardEntity & complete(keys)
                }
              }
            }
          }
        )
      },
      path("keys") {
        resource[DeviceKeyStore.View.Privileged] { view =>
          view.list().map { keys =>
            log.debugN("User [{}] successfully retrieved [{}] device keys", currentUser, keys.size)
            discardEntity & complete(keys)
          }
        }
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

object Devices {
  def apply()(implicit ctx: RoutesContext): Devices = new Devices()
}

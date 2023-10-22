package stasis.server.api.routes

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.server.model.devices.{DeviceBootstrapCodeStore, DeviceStore}
import stasis.server.model.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.devices.{DeviceBootstrapCodeGenerator, DeviceClientSecretGenerator, DeviceCredentialsManager}
import stasis.shared.model.devices.{DeviceBootstrapCode, DeviceBootstrapParameters}

import scala.concurrent.Future
import scala.util.control.NonFatal

class DeviceBootstrap(
  context: DeviceBootstrap.BootstrapContext
)(implicit ctx: RoutesContext)
    extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def codes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          resource[DeviceBootstrapCodeStore.View.Privileged] { bootstrapCodeView =>
            for {
              codes <- bootstrapCodeView.list()
            } yield {
              log.debugN("User [{}] successfully retrieved [{}] device bootstrap codes", currentUser, codes.size)
              discardEntity & complete(codes)
            }
          }
        }
      },
      path("for-device" / JavaUUID) { deviceId =>
        delete {
          resource[DeviceBootstrapCodeStore.Manage.Privileged] { bootstrapCodeManage =>
            for {
              deleted <- bootstrapCodeManage.delete(deviceId)
            } yield {
              if (deleted) {
                log.debugN("User [{}] successfully deleted bootstrap code for device [{}]", currentUser, deviceId)
              } else {
                log.warnN("User [{}] failed to delete bootstrap code for device [{}]", currentUser, deviceId)
              }

              discardEntity & complete(StatusCodes.OK)
            }
          }
        }
      },
      pathPrefix("own") {
        concat(
          pathEndOrSingleSlash {
            get {
              resources[
                DeviceStore.View.Self,
                DeviceBootstrapCodeStore.View.Self
              ] { (deviceView, bootstrapCodeView) =>
                for {
                  devices <- deviceView.list(currentUser).map(_.keys.toSeq)
                  codes <- bootstrapCodeView.list(devices)
                } yield {
                  log.debugN("User [{}] successfully retrieved [{}] own device bootstrap codes", currentUser, codes.size)
                  discardEntity & complete(codes)
                }
              }
            }
          },
          path("for-device" / JavaUUID) { deviceId =>
            concat(
              put {
                resources[DeviceStore.View.Self, DeviceBootstrapCodeStore.Manage.Self] { (deviceView, bootstrapCodeManage) =>
                  for {
                    devices <- deviceView.list(currentUser).map(_.keys.toSeq)
                    code <- context.bootstrapCodeGenerator.generate(currentUser, deviceId)
                    _ <- bootstrapCodeManage.put(devices, code)
                  } yield {
                    log.debugN("User [{}] successfully created bootstrap code for own device [{}]", currentUser, deviceId)
                    discardEntity & complete(code)
                  }
                }
              },
              delete {
                resources[DeviceStore.View.Self, DeviceBootstrapCodeStore.Manage.Self] { (deviceView, bootstrapCodeManage) =>
                  for {
                    devices <- deviceView.list(currentUser).map(_.keys.toSeq)
                    deleted <- bootstrapCodeManage.delete(devices, deviceId)
                  } yield {
                    if (deleted) {
                      log.debugN("User [{}] successfully deleted bootstrap code for own device [{}]", currentUser, deviceId)
                    } else {
                      log.warnN("User [{}] failed to delete bootstrap code for own device [{}]", currentUser, deviceId)
                    }

                    discardEntity & complete(StatusCodes.OK)
                  }
                }
              }
            )
          }
        )
      }
    )

  def execute(code: DeviceBootstrapCode)(implicit currentUser: CurrentUser): Route =
    pathEndOrSingleSlash {
      put {
        resources[
          DeviceStore.View.Self,
          UserStore.View.Self
        ] { (deviceView, userView) =>
          val result = for {
            devices <- deviceView.list(currentUser)
            device <- devices.get(code.device) match {
              case Some(device) => Future.successful(device)
              case None         => Future.failed(new IllegalStateException(s"Device [${code.device.toString}] not found"))
            }
            user <- userView.get(currentUser).flatMap {
              case Some(user) => Future.successful(user)
              case None       => Future.failed(new IllegalStateException(s"Current user [${currentUser.toString}] not found"))
            }
            clientSecret <- context.clientSecretGenerator.generate()
            clientId <- context.credentialsManager.setClientSecret(device = device, clientSecret = clientSecret)
          } yield {
            context.deviceParams
              .withDeviceInfo(
                device = code.device.toString,
                nodeId = device.node.toString,
                clientId = clientId,
                clientSecret = clientSecret
              )
              .withUserInfo(
                user = user.id.toString,
                userSalt = user.salt
              )
          }

          result
            .map { config =>
              log.debugN(
                "User [{}] successfully executed bootstrap for device [{}]",
                currentUser,
                code.device
              )

              discardEntity & complete(config)
            }
            .recover { case NonFatal(e: IllegalStateException) =>
              log.warnN(
                "User [{}] failed to execute bootstrap for device [{}]: [{}]",
                currentUser,
                code.device,
                e.getMessage
              )

              discardEntity & complete(StatusCodes.Conflict)
            }
        }
      }
    }
}

object DeviceBootstrap {
  final case class BootstrapContext(
    bootstrapCodeGenerator: DeviceBootstrapCodeGenerator,
    clientSecretGenerator: DeviceClientSecretGenerator,
    credentialsManager: DeviceCredentialsManager,
    deviceParams: DeviceBootstrapParameters
  )

  def apply(context: BootstrapContext)(implicit ctx: RoutesContext): DeviceBootstrap =
    new DeviceBootstrap(context)
}

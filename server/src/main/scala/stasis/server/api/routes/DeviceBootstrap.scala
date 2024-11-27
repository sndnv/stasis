package stasis.server.api.routes

import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.server.persistence.devices.DeviceBootstrapCodeStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.persistence.nodes.ServerNodeStore
import stasis.server.persistence.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.devices.DeviceBootstrapCodeGenerator
import stasis.server.security.devices.DeviceClientSecretGenerator
import stasis.server.security.devices.DeviceCredentialsManager
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.devices.DeviceBootstrapParameters

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
      pathPrefix("own") {
        concat(
          pathEndOrSingleSlash {
            get {
              resource[DeviceBootstrapCodeStore.View.Self] { bootstrapCodeView =>
                bootstrapCodeView.list(currentUser).map { codes =>
                  log.debugN("User [{}] successfully retrieved [{}] own device bootstrap codes", currentUser, codes.size)
                  discardEntity & complete(codes)
                }
              }
            }
          },
          path("for-device" / JavaUUID) { deviceId =>
            concat(
              put {
                resource[DeviceBootstrapCodeStore.Manage.Self] { bootstrapCodeManage =>
                  for {
                    code <- context.bootstrapCodeGenerator.generate(currentUser = currentUser, device = deviceId)
                    _ <- bootstrapCodeManage.put(currentUser, code)
                  } yield {
                    log.debugN("User [{}] successfully created bootstrap code for own device [{}]", currentUser, deviceId)
                    discardEntity & complete(code)
                  }
                }
              }
            )
          },
          path("for-device" / "new") {
            put {
              entity(as[CreateDeviceOwn]) { createRequest =>
                resource[DeviceBootstrapCodeStore.Manage.Self] { bootstrapCodeManage =>
                  for {
                    code <- context.bootstrapCodeGenerator.generate(currentUser = currentUser, request = createRequest)
                    _ <- bootstrapCodeManage.put(currentUser, code)
                  } yield {
                    log.debugN("User [{}] successfully created bootstrap code for a new device", currentUser)
                    discardEntity & complete(code)
                  }
                }
              }
            }
          },
          path(JavaUUID) { codeId =>
            delete {
              resource[DeviceBootstrapCodeStore.Manage.Self] { bootstrapCodeManage =>
                bootstrapCodeManage.delete(currentUser, codeId).map { deleted =>
                  if (deleted) {
                    log.debugN("User [{}] successfully deleted bootstrap code [{}] for own device", currentUser, codeId)
                  } else {
                    log.warnN("User [{}] failed to delete bootstrap code for own device", currentUser)
                  }

                  discardEntity & complete(StatusCodes.OK)
                }
              }
            }
          }
        )
      },
      path(JavaUUID) { codeId =>
        delete {
          resource[DeviceBootstrapCodeStore.Manage.Privileged] { bootstrapCodeManage =>
            bootstrapCodeManage.delete(codeId).map { deleted =>
              if (deleted) {
                log.debugN("User [{}] successfully deleted device bootstrap code [{}]", currentUser, codeId)
              } else {
                log.warnN("User [{}] failed to delete device bootstrap code", currentUser)
              }

              discardEntity & complete(StatusCodes.OK)
            }
          }
        }
      }
    )

  def execute(code: DeviceBootstrapCode)(implicit currentUser: CurrentUser): Route =
    pathEndOrSingleSlash {
      put {
        resources[
          DeviceStore.View.Self,
          DeviceStore.Manage.Self,
          UserStore.View.Self,
          ServerNodeStore.Manage.Self
        ] { (deviceView, deviceManage, userView, nodeManage) =>
          val result = for {
            user <- userView.get(currentUser).flatMap {
              case Some(user) => Future.successful(user)
              case None       => Future.failed(new IllegalStateException(s"Current user [${currentUser.toString}] not found"))
            }
            devices <- deviceView.list(currentUser)
            device <- code.target match {
              case Left(deviceId) =>
                devices.find(_.id == deviceId) match {
                  case Some(device) => Future.successful(device)
                  case None         => Future.failed(new IllegalStateException(s"Device [${deviceId.toString}] not found"))
                }

              case Right(request) =>
                val (device, node) = request.toDeviceAndNode(owner = user)
                for {
                  _ <- deviceManage.put(currentUser, device)
                  _ <- nodeManage.create(currentUser, device, node)
                } yield {
                  device
                }
            }
            clientSecret <- context.clientSecretGenerator.generate()
            clientId <- context.credentialsManager.setClientSecret(device = device, clientSecret = clientSecret)
          } yield {
            context.deviceParams
              .withDeviceInfo(
                device = device.id.toString,
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
                code.targetInfo
              )

              discardEntity & complete(config)
            }
            .recover { case NonFatal(e: IllegalStateException) =>
              log.warnN(
                "User [{}] failed to execute bootstrap for device [{}]: [{}]",
                currentUser,
                code.targetInfo,
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

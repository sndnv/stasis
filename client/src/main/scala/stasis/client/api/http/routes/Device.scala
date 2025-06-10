package stasis.client.api.http.routes

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.client.api.Context
import stasis.client.ops.commands.ProcessedCommand
import stasis.shared.api.requests.ReEncryptDeviceSecret

class Device()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.client.api.http.Formats.processedCommandFormat
  import stasis.client.api.http.Formats.serverStateFormat
  import stasis.shared.api.Formats.deviceFormat
  import stasis.shared.api.Formats.reEncryptDeviceSecretFormat

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(context.api.device()) { device =>
            log.debugN("API successfully retrieved device [{}] for user [{}]", device.id, device.owner)
            context.analytics.recordEvent(name = "get_device")

            consumeEntity & complete(device)
          }
        }
      },
      path("connections") {
        get {
          onSuccess(context.trackers.server.state) { state =>
            log.debugN("API successfully retrieved connection state for [{}] servers", state.size)
            context.analytics.recordEvent(name = "get_device_connections")

            consumeEntity & complete(state)
          }
        }
      },
      pathPrefix("key") {
        path("re-encrypt") {
          put {
            entity(as[ReEncryptDeviceSecret]) { request =>
              onSuccess(context.handlers.reEncryptDeviceSecret(request.userPassword.toCharArray)) { _ =>
                log.debug("API successfully re-encrypted device secret")
                context.analytics.recordEvent(name = "reencrypt_device_key")

                complete(StatusCodes.OK)
              }
            }
          }
        }
      },
      path("commands") {
        get {
          extractExecutionContext { implicit ec =>
            val result = for {
              commands <- context.commandProcessor.all()
              latest <- context.commandProcessor.lastProcessedCommand
            } yield {
              commands.map(c => ProcessedCommand(command = c, isProcessed = latest.forall(_ >= c.sequenceId)))
            }

            onSuccess(result) { commands =>
              log.debugN("API successfully retrieved [{}] command(s)", commands.size)
              context.analytics.recordEvent(name = "get_device_commands")

              consumeEntity & complete(commands)
            }
          }
        }
      }
    )
}

object Device {
  def apply()(implicit context: Context): Device =
    new Device()
}

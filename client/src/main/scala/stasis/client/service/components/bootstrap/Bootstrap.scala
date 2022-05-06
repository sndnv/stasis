package stasis.client.service.components.bootstrap

import akka.actor.typed.scaladsl.LoggerOps
import stasis.client.api.clients.{DefaultServerBootstrapEndpointClient, ServerBootstrapEndpointClient}
import stasis.shared.model.devices.DeviceBootstrapParameters

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait Bootstrap {
  def execute(): Future[DeviceBootstrapParameters]
}

object Bootstrap {
  def apply(base: Base, init: Init): Future[Bootstrap] = {
    import base._

    init.arguments().map { args =>
      val client: ServerBootstrapEndpointClient = DefaultServerBootstrapEndpointClient(
        serverBootstrapUrl = args.serverBootstrapUrl,
        acceptSelfSignedCertificates = args.acceptSelfSignedCertificates
      )

      new Bootstrap {
        override def execute(): Future[DeviceBootstrapParameters] = {
          log.infoN("Executing client bootstrap using server [{}]...", args.serverBootstrapUrl)
          val result = client.execute(bootstrapCode = args.bootstrapCode)

          result.onComplete {
            case Success(_) =>
              log.infoN(
                "Server [{}] successfully processed bootstrap request",
                args.serverBootstrapUrl
              )

            case Failure(e) =>
              log.errorN(
                "Client bootstrap using server [{}] failed: [{} - {}]",
                args.serverBootstrapUrl,
                e.getClass.getSimpleName,
                e.getMessage
              )
          }

          result
        }
      }
    }
  }
}

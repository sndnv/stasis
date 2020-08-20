package stasis.client.service.components.bootstrap

import stasis.client.api.clients.{DefaultServerBootstrapEndpointClient, ServerBootstrapEndpointClient}
import stasis.shared.model.devices.DeviceBootstrapParameters

import scala.concurrent.Future

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
        override def execute(): Future[DeviceBootstrapParameters] =
          client.execute(bootstrapCode = args.bootstrapCode)
      }
    }
  }
}

package stasis.client.api.clients

import scala.concurrent.Future

import stasis.shared.model.devices.DeviceBootstrapParameters

trait ServerBootstrapEndpointClient {
  def server: String

  def execute(bootstrapCode: String): Future[DeviceBootstrapParameters]
}

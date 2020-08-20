package stasis.client.api.clients

import stasis.shared.model.devices.DeviceBootstrapParameters

import scala.concurrent.Future

trait ServerBootstrapEndpointClient {
  def server: String

  def execute(bootstrapCode: String): Future[DeviceBootstrapParameters]
}

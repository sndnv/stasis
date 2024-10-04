package stasis.server.security.devices

import scala.concurrent.Future

import stasis.shared.model.devices.Device

trait DeviceCredentialsManager {
  def setClientSecret(device: Device, clientSecret: String): Future[String]
}

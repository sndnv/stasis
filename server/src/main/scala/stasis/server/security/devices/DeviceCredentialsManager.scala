package stasis.server.security.devices

import stasis.shared.model.devices.Device

import scala.concurrent.Future

trait DeviceCredentialsManager {
  def setClientSecret(device: Device, clientSecret: String): Future[String]
}

package stasis.server.security.mocks

import scala.concurrent.Future

import stasis.server.security.devices.DeviceCredentialsManager
import stasis.shared.model.devices.Device

class MockDeviceCredentialsManager extends DeviceCredentialsManager {
  override def setClientSecret(device: Device, clientSecret: String): Future[String] =
    Future.successful("test-client-id")
}

object MockDeviceCredentialsManager {
  def apply(): MockDeviceCredentialsManager = new MockDeviceCredentialsManager()
}

package stasis.test.specs.unit.server.security.mocks

import stasis.server.security.devices.DeviceCredentialsManager
import stasis.shared.model.devices.Device

import scala.concurrent.Future

class MockDeviceCredentialsManager extends DeviceCredentialsManager {
  override def setClientSecret(device: Device, clientSecret: String): Future[String] =
    Future.successful("test-client-id")
}

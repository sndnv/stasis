package stasis.test.specs.unit.server.security.mocks

import java.time.Instant

import scala.concurrent.Future

import stasis.server.security.CurrentUser
import stasis.server.security.devices.DeviceBootstrapCodeGenerator
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode

class MockDeviceBootstrapCodeGenerator extends DeviceBootstrapCodeGenerator {
  override def generate(currentUser: CurrentUser, device: Device.Id): Future[DeviceBootstrapCode] =
    Future.successful(
      DeviceBootstrapCode(
        value = "test-code",
        owner = currentUser.id,
        device = device,
        expiresAt = Instant.now().plusSeconds(42)
      )
    )
}

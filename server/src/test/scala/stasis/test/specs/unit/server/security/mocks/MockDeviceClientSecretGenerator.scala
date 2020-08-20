package stasis.test.specs.unit.server.security.mocks

import stasis.server.security.devices.DeviceClientSecretGenerator

import scala.concurrent.Future

class MockDeviceClientSecretGenerator extends DeviceClientSecretGenerator {
  override def generate(): Future[String] =
    Future.successful("test-secret")
}

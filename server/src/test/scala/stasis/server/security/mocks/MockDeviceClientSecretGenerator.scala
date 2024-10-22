package stasis.server.security.mocks

import scala.concurrent.Future

import stasis.server.security.devices.DeviceClientSecretGenerator

class MockDeviceClientSecretGenerator extends DeviceClientSecretGenerator {
  override def generate(): Future[String] =
    Future.successful("test-secret")
}

package stasis.server.security

import io.github.sndnv.layers.testing.UnitSpec

import stasis.server.security.mocks.MockDeviceCredentialsManager
import stasis.server.security.mocks.MockUserCredentialsManager

class CredentialsManagersSpec extends UnitSpec {
  "Default CredentialsManagers" should "provide user and device credentials managers" in {
    val users = MockUserCredentialsManager()
    val devices = MockDeviceCredentialsManager()
    val manager = CredentialsManagers.Default(users = users, devices = devices)

    manager.users should be(users)
    manager.devices should be(devices)
  }
}

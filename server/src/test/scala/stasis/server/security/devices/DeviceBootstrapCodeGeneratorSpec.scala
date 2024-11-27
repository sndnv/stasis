package stasis.server.security.devices

import java.time.Instant

import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec

class DeviceBootstrapCodeGeneratorSpec extends AsyncUnitSpec {
  "A DeviceBootstrapCodeGenerator" should "allow generating bootstrap codes for an existing device for the current user" in {
    val generator = createGenerator()

    val device = Device.generateId()
    val code = generator.generate(currentUser, device).await

    code.value.length should be(DeviceBootstrapCodeGenerator.MinCodeSize)
    code.target should be(Left(device))
    code.expiresAt.isAfter(Instant.now()) should be(true)
  }

  it should "allow generating bootstrap codes for a new device for the current user" in {
    val generator = createGenerator()

    val request = CreateDeviceOwn(name = "test-device", limits = None)
    val code = generator.generate(currentUser, request).await

    code.value.length should be(DeviceBootstrapCodeGenerator.MinCodeSize)
    code.target should be(Right(request))
    code.expiresAt.isAfter(Instant.now()) should be(true)
  }

  it should "fail if the configured code size is below the allowed minimum" in {
    an[IllegalArgumentException] should be thrownBy {
      DeviceBootstrapCodeGenerator(codeSize = 1, expiration = 1.second)
    }
  }

  private val currentUser = CurrentUser(id = User.generateId())

  private def createGenerator(): DeviceBootstrapCodeGenerator =
    DeviceBootstrapCodeGenerator(
      codeSize = DeviceBootstrapCodeGenerator.MinCodeSize,
      expiration = 1.second
    )(typedSystem.executionContext)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DeviceBootstrapCodeGeneratorSpec"
  )
}

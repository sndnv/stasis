package stasis.test.specs.unit.server.security.devices

import java.time.Instant

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.server.security.CurrentUser
import stasis.server.security.devices.DeviceBootstrapCodeGenerator
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.duration._

class DeviceBootstrapCodeGeneratorSpec extends AsyncUnitSpec {
  "A DeviceBootstrapCodeGenerator" should "allow generating device bootstrap codes for current user" in {
    val generator = createGenerator()

    val device = Device.generateId()
    val code = generator.generate(currentUser, device).await

    code.value.length should be(DeviceBootstrapCodeGenerator.MinCodeSize)
    code.device should be(device)
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

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DeviceBootstrapCodeGeneratorSpec"
  )
}

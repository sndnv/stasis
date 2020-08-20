package stasis.test.specs.unit.server.security.devices

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.server.security.devices.DeviceClientSecretGenerator
import stasis.test.specs.unit.AsyncUnitSpec

class DeviceClientSecretGeneratorSpec extends AsyncUnitSpec {
  "A DeviceClientSecretGenerator" should "allow generating device client secrets for current user" in {
    val generator = createGenerator()

    val secret = generator.generate().await
    secret.length should be(DeviceClientSecretGenerator.MinSecretSize)
  }

  it should "fail if the configured code size is below the allowed minimum" in {
    an[IllegalArgumentException] should be thrownBy {
      DeviceClientSecretGenerator(secretSize = 1)
    }
  }

  private def createGenerator(): DeviceClientSecretGenerator =
    DeviceClientSecretGenerator(
      secretSize = DeviceClientSecretGenerator.MinSecretSize
    )(typedSystem.executionContext)

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DeviceClientSecretGeneratorSpec"
  )
}

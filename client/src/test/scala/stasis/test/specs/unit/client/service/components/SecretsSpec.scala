package stasis.test.specs.unit.client.service.components

import java.util.UUID

import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.event.{Logging, LoggingAdapter}
import stasis.client.service.components.{Base, Files, Secrets}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{EncodingHelpers, ResourceHelpers}
import stasis.test.specs.unit.client.mocks.MockTokenEndpoint

import scala.collection.mutable
import scala.util.Try

class SecretsSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers {
  "A Secrets component" should "create itself from config" in {
    val encryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

    val username = "test-user"
    val password = "test-password".toCharArray
    val expectedUser = UUID.fromString("3256119f-068c-4c17-9184-2ec46f48ca54")
    val expectedDevice = UUID.fromString("bc3b2b9a-3d04-4c8c-a6bb-b4ee428d1a99")

    val tokenEndpointPort = ports.dequeue()
    val tokenEndpointConfigEntry = "stasis.client.server.authentication.token-endpoint"

    val endpoint = new MockTokenEndpoint(port = tokenEndpointPort, token = "test-token")
    endpoint.start()

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.write(path.resolve(Files.DeviceSecret), encryptedDeviceSecret.toArray)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$tokenEndpointConfigEntry: "http://localhost:$tokenEndpointPort/oauth/token"}"""
        )
      }
    )

    Secrets(
      base = Base(applicationDirectory = directory, terminate = () => ()).await,
      credentialsReader = () => Try((username, password))
    ).map { secrets =>
      endpoint.stop()
      secrets.deviceSecret.user should be(expectedUser)
      secrets.deviceSecret.device should be(expectedDevice)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "SecretsSpec"
  )

  private implicit val log: LoggingAdapter =
    Logging(typedSystem.toUntyped, this.getClass.getName)

  private val ports: mutable.Queue[Int] = (29000 to 29100).to[mutable.Queue]
}

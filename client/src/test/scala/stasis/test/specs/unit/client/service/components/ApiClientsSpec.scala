package stasis.test.specs.unit.client.service.components

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import akka.stream.StreamTcpException
import akka.util.ByteString
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceSecret, Secret}
import stasis.client.security.CredentialsProvider
import stasis.client.service.components.{ApiClients, Base, Secrets}
import stasis.core.packaging.Crate
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

import scala.concurrent.Future

class ApiClientsSpec extends AsyncUnitSpec with ResourceHelpers {
  "An ApiClients component" should "create itself from config" in {
    val deviceId = UUID.fromString("bc3b2b9a-3d04-4c8c-a6bb-b4ee428d1a99")
    val apiUrl = "http://localhost:19090"

    val coreNodeId = UUID.fromString("31f7c5b1-3d47-4731-8c2b-19f6416eb2e3")
    val coreAddress = "http://localhost:19091"

    implicit val secretsConfig: Secret.Config = Secret.Config(
      rawConfig = typedSystem.settings.config.getConfig("stasis.client.secrets"),
      ivSize = Aes.IvSize
    )

    val directory = createApplicationDirectory(init = _ => ())

    for {
      apiClients <- ApiClients(
        base = Base(applicationDirectory = directory, terminate = () => ()).await,
        secrets = new Secrets {
          override def deviceSecret: DeviceSecret = DeviceSecret(
            user = User.generateId(),
            device = deviceId,
            secret = ByteString.empty
          )

          override def credentialsProvider: CredentialsProvider = new CredentialsProvider {
            override def core: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
            override def api: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
          }
        }
      )
      pingFailure <- apiClients.clients.api.ping().failed
      pullFailure <- apiClients.clients.core.pull(Crate.generateId()).failed
    } yield {
      apiClients.clients.api.self should be(deviceId)
      apiClients.clients.api.server should be(apiUrl)
      pingFailure shouldBe a[StreamTcpException]
      pingFailure.getMessage should include("Connection refused")

      apiClients.clients.core.self should be(coreNodeId)
      apiClients.clients.core.server should be(coreAddress)
      pullFailure shouldBe a[StreamTcpException]
      pingFailure.getMessage should include("Connection refused")
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "ApiClientsSpec"
  )

  private implicit val log: LoggingAdapter =
    Logging(typedSystem.toUntyped, this.getClass.getName)
}

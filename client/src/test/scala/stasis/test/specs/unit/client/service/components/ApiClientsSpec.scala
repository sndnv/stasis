package stasis.test.specs.unit.client.service.components

import java.util.UUID

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.stream.StreamTcpException
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.security.CredentialsProvider
import stasis.client.service.ApplicationTray
import stasis.client.service.components.ApiClients
import stasis.client.service.components.Base
import stasis.client.service.components.Secrets
import stasis.core.packaging.Crate
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class ApiClientsSpec extends AsyncUnitSpec with ResourceHelpers {
  "An ApiClients component" should "create itself from config" in {
    val deviceId = UUID.fromString("bc3b2b9a-3d04-4c8c-a6bb-b4ee428d1a99")
    val apiUrl = "http://localhost:19090"

    val coreNodeId = UUID.fromString("31f7c5b1-3d47-4731-8c2b-19f6416eb2e3")
    val coreAddress = "http://localhost:19091"

    implicit val secretsConfig: SecretsConfig = SecretsConfig(
      config = typedSystem.settings.config.getConfig("stasis.client.secrets"),
      ivSize = Aes.IvSize
    )

    val directory = createApplicationDirectory(init = _ => ())

    for {
      apiClients <- ApiClients(
        base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await,
        secrets = new Secrets {
          override def deviceSecret: DeviceSecret =
            DeviceSecret(
              user = User.generateId(),
              device = deviceId,
              secret = ByteString.empty
            )

          override def credentialsProvider: CredentialsProvider =
            new CredentialsProvider {
              override def core: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
              override def api: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
            }

          override def config: SecretsConfig = secretsConfig

          override def verifyUserPassword: Array[Char] => Boolean = _ => false

          override def updateUserCredentials: (ServerApiEndpointClient, Array[Char], String) => Future[Done] =
            (_, _, _) => Future.successful(Done)

          override def reEncryptDeviceSecret: (ServerApiEndpointClient, Array[Char]) => Future[Done] =
            (_, _) => Future.successful(Done)
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

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ApiClientsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}

package stasis.test.specs.unit.identity.api.manage

import akka.http.scaladsl.model.StatusCodes
import akka.util.ByteString
import stasis.identity.api.Formats._
import stasis.identity.api.manage.Clients
import stasis.identity.api.manage.requests.{CreateClient, UpdateClient, UpdateClientCredentials}
import stasis.identity.api.manage.responses.CreatedClient
import stasis.identity.model.Seconds
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.ClientsSpec.PartialClient
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._

class ClientsSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "Clients routes" should "respond with all clients" in {
    val store = createClientStore()
    val clients = new Clients(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"

    val expectedClients = stasis.test.Generators
      .generateSeq(min = 2, g = Generators.generateClient)
      .map(_.copy(secret = secret, salt = salt))

    Future.sequence(expectedClients.map(store.put)).await
    Get() ~> clients.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[PartialClient]].map(_.toClient(secret, salt)).sortBy(_.id) should be(expectedClients.sortBy(_.id))
    }
  }

  they should "create new clients" in {
    val store = createClientStore()
    val clients = new Clients(store, secretConfig)

    val request = CreateClient(
      redirectUri = "some-uri",
      tokenExpiration = 3.seconds,
      rawSecret = "some-secret",
      subject = Some("some-subject")
    )

    Post().withEntity(request) ~> clients.routes(user) ~> check {
      status should be(StatusCodes.OK)
      val expectedClient = store.clients.await.values.toList match {
        case client :: Nil => client
        case other         => fail(s"Unexpected response received: [$other]")
      }

      responseAs[CreatedClient] should be(CreatedClient(client = expectedClient.id))
    }
  }

  they should "update existing client credentials" in {
    val store = createClientStore()
    val clients = new Clients(store, secretConfig)

    val client = Generators.generateClient
    val request = UpdateClientCredentials(rawSecret = "some-secret")

    store.put(client).await
    Put(s"/${client.id}/credentials").withEntity(request) ~> clients.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.get(client.id).await match {
        case Some(updatedClient) =>
          updatedClient.secret.isSameAs(request.rawSecret, updatedClient.salt)(secretConfig) should be(true)

        case None =>
          fail("Unexpected response received; no client found")
      }
    }
  }

  they should "respond with existing clients" in {
    val store = createClientStore()
    val clients = new Clients(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"

    val expectedClient = Generators.generateClient.copy(secret = secret, salt = salt)

    store.put(expectedClient).await
    Get(s"/${expectedClient.id}") ~> clients.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[PartialClient].toClient(secret, salt) should be(expectedClient)
    }
  }

  they should "update existing clients" in {
    val store = createClientStore()
    val clients = new Clients(store, secretConfig)

    val client = Generators.generateClient
    val request = UpdateClient(
      tokenExpiration = 3.seconds,
      active = false
    )

    store.put(client).await
    Put(s"/${client.id}").withEntity(request) ~> clients.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.get(client.id).await should be(
        Some(
          client.copy(
            tokenExpiration = request.tokenExpiration,
            active = request.active
          )
        )
      )
    }
  }

  they should "delete existing clients" in {
    val store = createClientStore()
    val clients = new Clients(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"

    val client = Generators.generateClient.copy(secret = secret, salt = salt)

    store.put(client).await
    Delete(s"/${client.id}") ~> clients.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.clients.await should be(Map.empty)
    }
  }

  they should "not delete missing clients" in {
    val store = createClientStore()
    val clients = new Clients(store, secretConfig)

    Delete(s"/${Client.generateId()}") ~> clients.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private val user = "some-user"

  private val secretConfig = Secret.ClientConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 20.millis
  )
}

object ClientsSpec {
  import play.api.libs.json._

  implicit val partialClientReads: Reads[PartialClient] = Json.reads[PartialClient]

  final case class PartialClient(
    id: Client.Id,
    redirectUri: String,
    tokenExpiration: Seconds,
    active: Boolean,
    subject: Option[String]
  ) {
    def toClient(secret: Secret, salt: String): Client =
      Client(
        id = id,
        redirectUri = redirectUri,
        tokenExpiration = tokenExpiration,
        secret = secret,
        salt = salt,
        active = active,
        subject = subject
      )
  }
}

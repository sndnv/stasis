package stasis.identity.service.bootstrap

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.model.Generators
import stasis.identity.model.Seconds
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.mocks.MockClientStore
import stasis.layers.UnitSpec

class ClientBootstrapEntityProviderSpec extends UnitSpec {
  "An ClientBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new ClientBootstrapEntityProvider(MockClientStore())

    provider.name should be("clients")

    provider.default should be(empty)
  }

  it should "support loading entities from config" in {
    val provider = new ClientBootstrapEntityProvider(MockClientStore())

    bootstrapConfig.getConfigList("clients").asScala.map(provider.load).toList match {
      case client1 :: client2 :: client3 :: Nil =>
        client1.redirectUri should be("http://localhost:8080/example/uri1")
        client1.tokenExpiration should be(Seconds(90 * 60))
        client1.active should be(true)
        client1.secret.isSameAs(
          rawSecret = "example-client-secret",
          salt = client1.salt
        )(clientSecretsConfig) should be(true)
        client1.subject should be(Some("example-subject"))

        client2.id should be(java.util.UUID.fromString("2c4311a6-f9a8-4c9f-8634-98afd90753e0"))
        client2.redirectUri should be("http://localhost:8080/example/uri2")
        client2.subject should be(None)

        client3.redirectUri should be("http://localhost:8080/example/uri3")
        client3.subject should be(Some("example-subject"))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "fail to load entities from config if a raw client secret is empty" in {
    val provider = new ClientBootstrapEntityProvider(MockClientStore())

    val e = intercept[IllegalArgumentException](
      bootstrapConfig.getConfigList("invalid-entities.clients").asScala.map(provider.load)
    )

    e.getMessage should include("Raw client secret cannot be empty")
  }

  it should "support validating entities" in {
    val provider = new ClientBootstrapEntityProvider(MockClientStore())

    val validClients = Seq(
      Generators.generateClient,
      Generators.generateClient,
      Generators.generateClient
    )

    val sharedId1 = Client.generateId()
    val sharedId2 = Client.generateId()

    val invalidClients = Seq(
      Generators.generateClient.copy(id = sharedId1),
      Generators.generateClient.copy(id = sharedId1),
      Generators.generateClient.copy(id = sharedId2),
      Generators.generateClient.copy(id = sharedId2)
    )

    noException should be thrownBy provider.validate(validClients).await

    provider.validate(invalidClients).failed.await.getMessage should (be(
      s"Duplicate values [${sharedId1.toString},${sharedId2.toString}] found for field [id] in [Client]"
    ) or be(s"Duplicate values [${sharedId2.toString},${sharedId1.toString}] found for field [id] in [Client]"))
  }

  it should "support creating entities" in {
    val store = MockClientStore()
    val provider = new ClientBootstrapEntityProvider(store)

    for {
      existingBefore <- store.all
      _ <- provider.create(Generators.generateClient)
      existingAfter <- store.all
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new ClientBootstrapEntityProvider(MockClientStore())

    val clientWithSubject = Generators.generateClient.copy(subject = Some("abc"))
    provider.render(clientWithSubject, withPrefix = "") should be(
      s"""
         |  client:
         |    id:               ${clientWithSubject.id}
         |    redirect-uri:     ${clientWithSubject.redirectUri}
         |    token-expiration: ${clientWithSubject.tokenExpiration.value.toString}
         |    secret:           ***
         |    salt:             ***
         |    active:           ${clientWithSubject.active.toString}
         |    subject:          abc
         |    created:          ${clientWithSubject.created.toString}
         |    updated:          ${clientWithSubject.updated.toString}""".stripMargin
    )

    val clientWithoutSubject = Generators.generateClient.copy(subject = None)
    provider.render(clientWithoutSubject, withPrefix = "") should be(
      s"""
         |  client:
         |    id:               ${clientWithoutSubject.id}
         |    redirect-uri:     ${clientWithoutSubject.redirectUri}
         |    token-expiration: ${clientWithoutSubject.tokenExpiration.value.toString}
         |    secret:           ***
         |    salt:             ***
         |    active:           ${clientWithoutSubject.active.toString}
         |    subject:          -
         |    created:          ${clientWithoutSubject.created.toString}
         |    updated:          ${clientWithoutSubject.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new ClientBootstrapEntityProvider(MockClientStore())

    val client = Generators.generateClient

    provider.extractId(client) should be(client.id.toString)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ClientBootstrapEntityProviderSpec"
  )

  private implicit val clientSecretsConfig: Secret.ClientConfig =
    Secret.ClientConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 20.millis
    )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}

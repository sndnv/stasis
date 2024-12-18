package stasis.identity.service.bootstrap

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.model.Generators
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.mocks.MockResourceOwnerStore
import stasis.layers.UnitSpec

class ResourceOwnerBootstrapEntityProviderSpec extends UnitSpec {
  "An ResourceOwnerBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new ResourceOwnerBootstrapEntityProvider(MockResourceOwnerStore())

    provider.name should be("owners")

    provider.default should be(empty)
  }

  it should "support loading entities from config" in {
    val provider = new ResourceOwnerBootstrapEntityProvider(MockResourceOwnerStore())

    bootstrapConfig.getConfigList("owners").asScala.map(provider.load).toList match {
      case owner :: Nil =>
        owner.username should be("example-user")
        owner.allowedScopes should be(Seq("example-scope-a", "example-scope-b", "example-scope-c"))
        owner.active should be(true)
        owner.password.isSameAs(
          rawSecret = "example-user-password",
          salt = owner.salt
        )(ownerSecretsConfig) should be(true)
        owner.subject should be(Some("example-subject"))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "fail to load entities from config if a raw resource owner password is empty" in {
    val provider = new ResourceOwnerBootstrapEntityProvider(MockResourceOwnerStore())

    val e = intercept[IllegalArgumentException](
      bootstrapConfig.getConfigList("invalid-entities.owners").asScala.map(provider.load)
    )

    e.getMessage should include("Raw resource owner password cannot be empty")
  }

  it should "support validating entities" in {
    val provider = new ResourceOwnerBootstrapEntityProvider(MockResourceOwnerStore())

    val validOwners = Seq(
      Generators.generateResourceOwner,
      Generators.generateResourceOwner
    )

    val invalidOwners = Seq(
      Generators.generateResourceOwner.copy(username = "a"),
      Generators.generateResourceOwner.copy(username = "a"),
      Generators.generateResourceOwner.copy(username = "b"),
      Generators.generateResourceOwner.copy(username = "b")
    )

    noException should be thrownBy provider.validate(validOwners).await

    provider.validate(invalidOwners).failed.await.getMessage should (be(
      s"Duplicate values [a,b] found for field [username] in [ResourceOwner]"
    ) or be(s"Duplicate values [b,a] found for field [username] in [ResourceOwner]"))
  }

  it should "support creating entities" in {
    val store = MockResourceOwnerStore()
    val provider = new ResourceOwnerBootstrapEntityProvider(store)

    for {
      existingBefore <- store.all
      _ <- provider.create(Generators.generateResourceOwner)
      existingAfter <- store.all
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new ResourceOwnerBootstrapEntityProvider(MockResourceOwnerStore())

    val ownerWithSubject = Generators.generateResourceOwner.copy(subject = Some("abc"))
    provider.render(ownerWithSubject, withPrefix = "") should be(
      s"""
         |  resource-owner:
         |    username:       ${ownerWithSubject.username}
         |    password:       ***
         |    salt:           ***
         |    allowed-scopes: ${ownerWithSubject.allowedScopes.mkString(", ")}
         |    active:         ${ownerWithSubject.active.toString}
         |    subject:        abc
         |    created:        ${ownerWithSubject.created.toString}
         |    updated:        ${ownerWithSubject.updated.toString}""".stripMargin
    )

    val ownerWithoutSubject = Generators.generateResourceOwner.copy(subject = None)
    provider.render(ownerWithoutSubject, withPrefix = "") should be(
      s"""
         |  resource-owner:
         |    username:       ${ownerWithoutSubject.username}
         |    password:       ***
         |    salt:           ***
         |    allowed-scopes: ${ownerWithoutSubject.allowedScopes.mkString(", ")}
         |    active:         ${ownerWithoutSubject.active.toString}
         |    subject:        -
         |    created:        ${ownerWithoutSubject.created.toString}
         |    updated:        ${ownerWithoutSubject.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new ResourceOwnerBootstrapEntityProvider(MockResourceOwnerStore())

    val owner = Generators.generateResourceOwner

    provider.extractId(owner) should be(owner.username)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ResourceOwnerBootstrapEntityProviderSpec"
  )

  private implicit val ownerSecretsConfig: Secret.ResourceOwnerConfig =
    Secret.ResourceOwnerConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 20.millis
    )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}

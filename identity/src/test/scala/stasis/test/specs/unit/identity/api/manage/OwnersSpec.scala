package stasis.test.specs.unit.identity.api.manage

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.util.ByteString

import stasis.identity.api.Formats._
import stasis.identity.api.manage.Owners
import stasis.identity.api.manage.requests.CreateOwner
import stasis.identity.api.manage.requests.UpdateOwner
import stasis.identity.api.manage.requests.UpdateOwnerCredentials
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.layers
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.OwnersSpec.PartialResourceOwner
import stasis.test.specs.unit.identity.model.Generators

class OwnersSpec extends RouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "Owners routes" should "respond with all resource owners" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"

    val expectedOwners = layers.Generators
      .generateSeq(min = 2, g = Generators.generateResourceOwner)
      .map(_.copy(password = secret, salt = salt).truncated())

    Future.sequence(expectedOwners.map(store.put)).await
    Get() ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[PartialResourceOwner]].map(_.toOwner(secret, salt).truncated()).sortBy(_.username) should be(
        expectedOwners.sortBy(_.username)
      )
    }
  }

  they should "create new resource owners" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val request = CreateOwner(
      username = "some-user",
      rawPassword = "some-password",
      allowedScopes = Seq("some-scope"),
      subject = Some("some-subject")
    )

    Post().withEntity(request) ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      val createdOwner = store.all.await.toList match {
        case owner :: Nil => owner
        case other        => fail(s"Unexpected response received; [$other]")
      }

      createdOwner.username should be(request.username)
    }
  }

  they should "reject creation requests for existing users" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val request = CreateOwner(
      username = "some-user",
      rawPassword = "some-password",
      allowedScopes = Seq("some-scope"),
      subject = Some("some-subject")
    )

    Post().withEntity(request) ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.all.await.size should be(1)

      Post().withEntity(request) ~> owners.routes(user) ~> check {
        status should be(StatusCodes.Conflict)
        store.all.await.size should be(1)
      }
    }
  }

  they should "respond with resource owner when queried by subject" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"

    val expectedOwners = layers.Generators
      .generateSeq(min = 3, g = Generators.generateResourceOwner)
      .map(_.copy(password = secret, salt = salt))
      .zipWithIndex
      .map { case (owner, i) =>
        owner.copy(subject = Some(s"test-subject-$i"))
      }

    Future.sequence(expectedOwners.map(store.put)).await

    Get("/by-subject/test-subject-1") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[PartialResourceOwner].subject should be(Some("test-subject-1"))
    }
  }

  they should "update existing resource owner credentials when queried by subject" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val ownerSubject = "test-subject"
    val owner = Generators.generateResourceOwner.copy(subject = Some(ownerSubject))
    val request = UpdateOwnerCredentials(rawPassword = "some-password")

    store.put(owner).await

    Put(s"/by-subject/$ownerSubject/credentials").withEntity(request) ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.get(owner.username).await match {
        case Some(updatedOwner) =>
          updatedOwner.password.isSameAs(request.rawPassword, updatedOwner.salt)(secretConfig) should be(true)

        case None =>
          fail("Unexpected response received; no resource owner found")
      }
    }
  }

  they should "activate existing resource owner when queried by subject" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val ownerSubject = "test-subject"
    val owner = Generators.generateResourceOwner.copy(subject = Some(ownerSubject), active = false)

    store.put(owner).await

    Put(s"/by-subject/$ownerSubject/activate") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)

      store.get(owner.username).await match {
        case Some(updatedOwner) =>
          updatedOwner.active should be(true)

        case None =>
          fail("Unexpected response received; no resource owner found")
      }
    }
  }

  they should "deactivate existing resource owner when queried by subject" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val ownerSubject = "test-subject"
    val owner = Generators.generateResourceOwner.copy(subject = Some(ownerSubject), active = true)

    store.put(owner).await

    Put(s"/by-subject/$ownerSubject/deactivate") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)

      store.get(owner.username).await match {
        case Some(updatedOwner) =>
          updatedOwner.active should be(false)

        case None =>
          fail("Unexpected response received; no resource owner found")
      }
    }
  }

  they should "reject resource owner requests by subject when no owner is found" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    Get("/by-subject/test-subject") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "reject resource owner requests by subject when more than one owner is found" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"

    val ownersSubject = "test-subject"

    val expectedOwners = layers.Generators
      .generateSeq(min = 3, g = Generators.generateResourceOwner)
      .map(_.copy(password = secret, salt = salt, subject = Some(ownersSubject)))

    Future.sequence(expectedOwners.map(store.put)).await

    Get(s"/by-subject/$ownersSubject") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.Conflict)
    }
  }

  they should "update existing resource owner credentials" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val owner = Generators.generateResourceOwner
    val request = UpdateOwnerCredentials(rawPassword = "some-password")

    store.put(owner).await
    Put(s"/${owner.username}/credentials").withEntity(request) ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.get(owner.username).await match {
        case Some(updatedOwner) =>
          updatedOwner.password.isSameAs(request.rawPassword, updatedOwner.salt)(secretConfig) should be(true)

        case None =>
          fail("Unexpected response received; no resource owner found")
      }
    }
  }

  they should "respond with existing resource owners" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"
    val expectedOwner = Generators.generateResourceOwner.copy(password = secret, salt = salt)

    store.put(expectedOwner).await
    Get(s"/${expectedOwner.username}") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[PartialResourceOwner].toOwner(secret, salt).truncated() should be(expectedOwner.truncated())
    }
  }

  they should "update existing resource owners" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val owner = Generators.generateResourceOwner
    val request = UpdateOwner(
      allowedScopes = Seq("some-scope"),
      active = false
    )

    store.put(owner).await
    Put(s"/${owner.username}").withEntity(request) ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.get(owner.username).await.truncated() should be(
        Some(
          owner
            .copy(
              allowedScopes = request.allowedScopes,
              active = request.active
            )
            .truncated()
        )
      )
    }
  }

  they should "delete existing resource owners" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val owner = Generators.generateResourceOwner

    store.put(owner).await
    store.all.await.size should be(1)

    Delete(s"/${owner.username}") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.all.await.size should be(0)
    }
  }

  they should "not delete missing resource owners" in withRetry {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    Delete("/some-user") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private val user = "some-user"

  private val secretConfig = Secret.ResourceOwnerConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 20.millis
  )
}

object OwnersSpec {
  import play.api.libs.json._

  implicit val partialResourceOwnerReads: Reads[PartialResourceOwner] = Json.reads[PartialResourceOwner]

  final case class PartialResourceOwner(
    username: ResourceOwner.Id,
    allowedScopes: Seq[String],
    active: Boolean,
    subject: Option[String]
  ) {
    def toOwner(secret: Secret, salt: String): ResourceOwner =
      ResourceOwner.create(
        username = username,
        password = secret,
        salt = salt,
        allowedScopes = allowedScopes,
        active = active,
        subject = subject
      )
  }
}

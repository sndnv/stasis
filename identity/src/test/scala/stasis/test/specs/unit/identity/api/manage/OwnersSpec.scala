package stasis.test.specs.unit.identity.api.manage

import akka.http.scaladsl.model.StatusCodes
import akka.util.ByteString
import stasis.identity.api.Formats._
import stasis.identity.api.manage.Owners
import stasis.identity.api.manage.requests.{CreateOwner, UpdateOwner, UpdateOwnerCredentials}
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.OwnersSpec.PartialResourceOwner
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._

class OwnersSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "Owners routes" should "respond with all resource owners" in {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"

    val expectedOwners = stasis.test.Generators
      .generateSeq(min = 2, g = Generators.generateResourceOwner)
      .map(_.copy(password = secret, salt = salt))

    Future.sequence(expectedOwners.map(store.put)).await
    Get() ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[PartialResourceOwner]].map(_.toOwner(secret, salt)).sortBy(_.username) should be(
        expectedOwners.sortBy(_.username)
      )
    }
  }

  they should "create new resource owners" in {
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
      val createdOwner = store.owners.await.values.toList match {
        case owner :: Nil => owner
        case other        => fail(s"Unexpected response received; [$other]")
      }

      createdOwner.username should be(request.username)
    }
  }

  they should "reject creation requests for existing users" in {
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
      store.owners.await.size should be(1)

      Post().withEntity(request) ~> owners.routes(user) ~> check {
        status should be(StatusCodes.Conflict)
        store.owners.await.size should be(1)
      }
    }
  }

  they should "update existing resource owner credentials" in {
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

  they should "respond with existing resource owners" in {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val secret = Secret(ByteString("some-secret"))
    val salt = "some-salt"
    val expectedOwner = Generators.generateResourceOwner.copy(password = secret, salt = salt)

    store.put(expectedOwner).await
    Get(s"/${expectedOwner.username}") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[PartialResourceOwner].toOwner(secret, salt) should be(expectedOwner)
    }
  }

  they should "update existing resource owners" in {
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
      store.get(owner.username).await should be(
        Some(
          owner.copy(
            allowedScopes = request.allowedScopes,
            active = request.active
          )
        )
      )
    }
  }

  they should "delete existing resource owners" in {
    val store = createOwnerStore()
    val owners = new Owners(store, secretConfig)

    val owner = Generators.generateResourceOwner

    store.put(owner).await
    Delete(s"/${owner.username}") ~> owners.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.owners.await should be(Map.empty)
    }
  }

  they should "not delete missing resource owners" in {
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
      ResourceOwner(
        username = username,
        password = secret,
        salt = salt,
        allowedScopes = allowedScopes,
        active = active,
        subject = subject
      )
  }
}

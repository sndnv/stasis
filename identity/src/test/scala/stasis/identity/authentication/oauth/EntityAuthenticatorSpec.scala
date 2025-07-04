package stasis.identity.authentication.oauth

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials

import stasis.identity.model.secrets.Secret
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.security.exceptions.AuthenticationFailure

class EntityAuthenticatorSpec extends UnitSpec {
  "An EntityAuthenticator" should "successfully authenticate entities" in {
    val authenticator = new MockEntityAuthenticator(secretConfig)

    val entity = "some-entity"

    authenticator
      .authenticate(BasicHttpCredentials(username = entity, password = entity))
      .map { result =>
        result should be(entity)
      }
  }

  it should "fail to authenticate entities with invalid credentials" in {
    val authenticator = new MockEntityAuthenticator(secretConfig)

    val entity = "some-entity"

    authenticator
      .authenticate(BasicHttpCredentials(username = entity, password = "invalid-password"))
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage should be(s"Credentials for entity [$entity] do not match")
      }
  }

  it should "provide authentication responses after a pre-configured delay (success)" in {
    val expectedDelay = 200.millis
    val authenticator = new MockEntityAuthenticator(secretConfig.copy(authenticationDelay = expectedDelay))

    val entity = "some-entity"

    val start = System.currentTimeMillis()

    authenticator
      .authenticate(BasicHttpCredentials(username = entity, password = entity))
      .map { result =>
        val end = System.currentTimeMillis()
        val duration = (end - start).millis

        result should be(entity)
        duration should be >= expectedDelay
      }
  }

  it should "provide authentication responses after a pre-configured delay (failure)" in {
    val expectedDelay = 200.millis
    val authenticator = new MockEntityAuthenticator(
      secretConfig = secretConfig.copy(authenticationDelay = expectedDelay),
      failingGet = true
    )

    val entity = "some-entity"

    val start = System.currentTimeMillis()

    authenticator
      .authenticate(BasicHttpCredentials(username = entity, password = entity))
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        val end = System.currentTimeMillis()
        val duration = (end - start).millis

        e.getMessage should be("failure")
        duration should be >= expectedDelay
      }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "EntityAuthenticatorSpec"
  )

  private implicit val secretConfig: Secret.ClientConfig = Secret.ClientConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 50.millis
  )

  private class MockEntityAuthenticator(secretConfig: Secret.Config, failingGet: Boolean = false)(implicit
    protected val system: ActorSystem[Nothing]
  ) extends EntityAuthenticator[String] {

    override implicit protected def config: Secret.Config = secretConfig

    override protected def getEntity(username: String): Future[String] =
      if (failingGet) {
        Future.failed(new Exception("failure"))
      } else {
        Future.successful(username)
      }

    override protected def extractSecret: String => Secret =
      entity => Secret.derive(rawSecret = entity, salt = entity)

    override protected def extractSalt: String => String =
      entity => entity
  }
}

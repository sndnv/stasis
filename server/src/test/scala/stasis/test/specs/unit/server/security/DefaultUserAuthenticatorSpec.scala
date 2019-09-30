package stasis.test.specs.unit.server.security

import java.security.Key

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import org.jose4j.jws.AlgorithmIdentifiers
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.JwtAuthenticator
import stasis.core.security.keys.KeyProvider
import stasis.server.model.users.UserStore
import stasis.server.security.DefaultUserAuthenticator
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.{MockJwksGenerators, MockJwtGenerators}
import stasis.test.specs.unit.server.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class DefaultUserAuthenticatorSpec extends AsyncUnitSpec { test =>
  "A DefaultUserAuthenticator" should "authenticate user with valid JWTs" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = audience,
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    store.manage().create(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { actualUser =>
        actualUser.id should be(expectedUser.id)
      }
  }

  it should "fail to authenticate users with unexpected credentials" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = audience,
        expirationTolerance = 10.seconds
      )
    )

    store.manage().create(expectedUser).await
    authenticator
      .authenticate(
        credentials = BasicHttpCredentials(username = "some-username", password = "some-password")
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage should be("Unsupported user credentials provided: [Basic]")
      }
  }

  it should "fail to authenticate inactive users" in {
    val store = createStore()

    val expectedUser = Generators.generateUser.copy(active = false)

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = audience,
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    store.manage().create(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage should be(s"User [${expectedUser.id}] is not active")
      }
  }

  it should "fail to authenticate users with invalid JWTs" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val invalidAudience = "invalid-audience"

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = invalidAudience,
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    store.manage().create(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Expected $invalidAudience as an aud value") should be(true)
      }
  }

  it should "fail to authenticate users with invalid user IDs" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val invalidUserId = "invalid-user-id"

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = audience,
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = invalidUserId)

    store.manage().create(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe an[IllegalArgumentException]
          e.getMessage should be(s"Invalid UUID string: $invalidUserId")
      }
  }

  it should "fail to authenticate missing users" in {
    val store = createStore()

    val expectedUser = Generators.generateUser.copy(active = false)

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = audience,
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage should be(s"User [${expectedUser.id}] not found")
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "DefaultUserAuthenticatorSpec"
  )

  private val issuer = "some-issuer"
  private val audience = "some-audience"

  private val jwk = MockJwksGenerators.generateRandomRsaKey(keyId = Some("some-key"))

  private val provider = new KeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)
    override def issuer: String = test.issuer
    override def allowedAlgorithms: Seq[String] = Seq(
      AlgorithmIdentifiers.RSA_USING_SHA256,
      AlgorithmIdentifiers.RSA_USING_SHA384,
      AlgorithmIdentifiers.RSA_USING_SHA512
    )
  }

  private def generateToken(subject: String): String = MockJwtGenerators.generateJwt(
    issuer = issuer,
    audience = audience,
    subject = subject,
    signatureKey = jwk
  )

  private def createStore() = UserStore(
    MemoryBackend[User.Id, User](name = s"user-store-${java.util.UUID.randomUUID()}")
  )
}

package stasis.test.specs.unit.identity.authentication.manage

import java.security.Key
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.jose4j.jws.AlgorithmIdentifiers
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.DefaultJwtAuthenticator
import stasis.core.security.keys.KeyProvider
import stasis.identity.authentication.manage.DefaultResourceOwnerAuthenticator
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.tokens.generators.JwtBearerAccessTokenGenerator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.MockJwksGenerators
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class DefaultResourceOwnerAuthenticatorSpec extends AsyncUnitSpec { test =>
  "A DefaultResourceOwnerAuthenticator" should "authenticate resource owners with valid JWTs" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner.copy(subject = None)
    val targetApi = Generators.generateApi

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = targetApi.id,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val accessToken = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    for {
      _ <- store.put(expectedOwner)
      actualOwner <- authenticator.authenticate(credentials = OAuth2BearerToken(accessToken.token.value))
    } yield {
      actualOwner should be(expectedOwner)
    }
  }

  it should "fail to authenticate inactive resource owners" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner.copy(active = false, subject = None)
    val targetApi = Generators.generateApi

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = targetApi.id,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val accessToken = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    store
      .put(expectedOwner)
      .flatMap { _ =>
        authenticator.authenticate(credentials = OAuth2BearerToken(accessToken.token.value))
      }
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Resource owner [${expectedOwner.username}] is not active") should be(true)
      }
  }

  it should "fail to authenticate resource owners with invalid JWTs" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner.copy(subject = None)
    val targetApi = Generators.generateApi

    val invalidAudience = "some-audience"

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = invalidAudience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val accessToken = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    store
      .put(expectedOwner)
      .flatMap { _ =>
        authenticator.authenticate(credentials = OAuth2BearerToken(accessToken.token.value))
      }
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Expected $invalidAudience as an aud value") should be(true)
      }
  }

  it should "fail to authenticate missing resource owners" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner.copy(subject = None)
    val targetApi = Generators.generateApi

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = targetApi.id,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val accessToken = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    authenticator
      .authenticate(credentials = OAuth2BearerToken(accessToken.token.value))
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Resource owner [${expectedOwner.username}] not found") should be(true)
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultResourceOwnerAuthenticatorSpec-manage"
  )

  private val issuer = "some-issuer"

  private val jwk = MockJwksGenerators.generateRandomRsaKey(Some("some-key"))

  private val provider = new KeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)
    override def issuer: String = test.issuer
    override def allowedAlgorithms: Seq[String] =
      Seq(
        AlgorithmIdentifiers.RSA_USING_SHA256,
        AlgorithmIdentifiers.RSA_USING_SHA384,
        AlgorithmIdentifiers.RSA_USING_SHA512
      )
  }

  private val tokenGenerator = new JwtBearerAccessTokenGenerator(
    issuer = test.issuer,
    jwk = jwk,
    jwtExpiration = 3.seconds
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private def createStore() =
    ResourceOwnerStore(
      MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
    )
}

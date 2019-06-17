package stasis.test.specs.unit.identity.authentication.manage

import java.security.Key

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import org.jose4j.jws.AlgorithmIdentifiers
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.{JwtAuthenticator, JwtKeyProvider}
import stasis.identity.authentication.manage.DefaultResourceOwnerAuthenticator
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.tokens.generators.JwtBearerAccessTokenGenerator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.jwt.mocks.MockJwksGenerators
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class DefaultResourceOwnerAuthenticatorSpec extends AsyncUnitSpec { test =>
  "A DefaultResourceOwnerAuthenticator" should "authenticate resource owners with valid JWTs" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner
    val targetApi = Generators.generateApi

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = targetApi.id.toString,
        expirationTolerance = 10.seconds
      )
    )

    val token = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    for {
      _ <- store.put(expectedOwner)
      actualOwner <- authenticator.authenticate(credentials = OAuth2BearerToken(token.value))
    } yield {
      actualOwner should be(expectedOwner)
    }
  }

  it should "fail to authenticate inactive resource owners" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner.copy(active = false)
    val targetApi = Generators.generateApi

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = targetApi.id.toString,
        expirationTolerance = 10.seconds
      )
    )

    val token = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    store
      .put(expectedOwner)
      .flatMap { _ =>
        authenticator.authenticate(credentials = OAuth2BearerToken(token.value))
      }
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Resource owner [${expectedOwner.username}] is not active") should be(true)
      }
  }

  it should "fail to authenticate resource owners with invalid JWTs" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner
    val targetApi = Generators.generateApi

    val invalidAudience = "some-audience"

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = invalidAudience,
        expirationTolerance = 10.seconds
      )
    )

    val token = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    store
      .put(expectedOwner)
      .flatMap { _ =>
        authenticator.authenticate(credentials = OAuth2BearerToken(token.value))
      }
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Expected $invalidAudience as an aud value") should be(true)
      }
  }

  it should "fail to authenticate missing resource owners" in {
    val store = createStore()

    val expectedOwner = Generators.generateResourceOwner
    val targetApi = Generators.generateApi

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = targetApi.id.toString,
        expirationTolerance = 10.seconds
      )
    )

    val token = tokenGenerator.generate(expectedOwner, audience = Seq(targetApi))

    authenticator
      .authenticate(credentials = OAuth2BearerToken(token.value))
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Resource owner [${expectedOwner.username}] not found") should be(true)
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "DefaultResourceOwnerAuthenticatorSpec-manage"
  )

  private val issuer = "some-issuer"

  private val jwk = MockJwksGenerators.generateRandomRsaKey(Some("some-key"))

  private val provider = new JwtKeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)
    override def issuer: String = test.issuer
    override def allowedAlgorithms: Seq[String] = Seq(AlgorithmIdentifiers.RSA_USING_SHA256)
  }

  private val tokenGenerator = new JwtBearerAccessTokenGenerator(
    issuer = test.issuer,
    jwk = jwk,
    jwtExpiration = 3.seconds
  )

  private def createStore() = ResourceOwnerStore(
    MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
  )
}

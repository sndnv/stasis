package stasis.test.specs.unit.server.service

import java.security.SecureRandom
import java.util.UUID

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import com.typesafe.config.{Config, ConfigFactory}
import javax.net.ssl.{SSLContext, TrustManagerFactory}
import org.jose4j.jwk.JsonWebKey
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.core.networking.http.{HttpEndpointAddress, HttpEndpointClient}
import stasis.core.packaging.Crate
import stasis.core.security.tls.EndpointContext
import stasis.server.service.{CorePersistence, ServerPersistence, Service}
import stasis.shared.api.requests.CreateUser
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.{MockJwksEndpoint, MockJwtGenerators}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class ServiceSpec extends AsyncUnitSpec with ScalatestRouteTest with Eventually {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  "Service" should "handle API requests" in {
    implicit val trustedContext: HttpsConnectionContext = createTrustedContext()

    val jwksPort = 29998
    val jwksEndpoint = new MockJwksEndpoint(port = jwksPort)
    val defaultJwk = jwksEndpoint.jwks.getJsonWebKeys.asScala.find(_.getKeyType != "oct") match {
      case Some(jwk) => jwk
      case None      => fail("Expected at least one mock JWK but none were found")
    }
    jwksEndpoint.start()

    val service = new Service {}
    val interface = "localhost"

    val servicePort = 29999
    val serviceUrl = s"https://$interface:$servicePort"

    val corePort = 39999
    val coreUrl = s"https://$interface:$corePort"

    val (serverPersistence, corePersistence) = eventually[(ServerPersistence, CorePersistence)] {
      service.state match {
        case Service.State.Started(apiServices, coreServices) => (apiServices.persistence, coreServices.persistence)
        case state                                            => fail(s"Unexpected service state encountered: [$state]")
      }
    }

    val existingUser = UUID.fromString("749d8c0e-6105-4022-ae0e-39bd77752c5d")

    val createUserRequest = CreateUser(
      limits = None,
      permissions = Set(
        Permission.View.Service
      )
    )

    val existingNode = "8d470761-7c15-4060-a268-a13a57f57d4c"

    val coreAddress = HttpEndpointAddress(coreUrl)
    val coreClient = HttpEndpointClient(
      credentials = {
        case `coreAddress` => getJwt(subject = existingNode, signatureKey = defaultJwk).map(OAuth2BearerToken)
        case address       => Future.failed(new IllegalArgumentException(s"Unexpected address provided: [$address]"))
      },
      context = trustedContext,
      requestBufferSize = 100
    )

    for {
      jwt <- getJwt(subject = existingUser.toString, signatureKey = defaultJwk)
      usersBefore <- serverPersistence.users.view().list().map(_.values.toSeq)
      apiUsersBefore <- getUsers(serviceUrl, jwt)
      _ <- createUser(serviceUrl, createUserRequest, jwt)
      usersAfter <- serverPersistence.users.view().list().map(_.values.toSeq)
      apiUsersAfter <- getUsers(serviceUrl, jwt)
      crateDiscarded <- coreClient.discard(address = coreAddress, crate = Crate.generateId())
      _ <- serverPersistence.drop()
      _ <- corePersistence.drop()
      _ = service.stop()
      _ = jwksEndpoint.stop()
    } yield {
      usersBefore should be(apiUsersBefore)
      apiUsersBefore.map(_.id) should be(Seq(existingUser))

      usersAfter should be(apiUsersAfter)
      apiUsersAfter should have size 2
      apiUsersAfter.filter(_.id != existingUser).toList match {
        case newUser :: Nil =>
          newUser.active should be(true)
          newUser.limits should be(None)
          newUser.permissions should be(createUserRequest.permissions)

        case other =>
          fail(s"Unexpected response received: [$other]")
      }

      crateDiscarded should be(false)
    }
  }

  it should "handle bootstrap failures" in {
    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory.load("application-invalid-bootstrap")
    }

    eventually[Assertion] {
      service.state shouldBe a[Service.State.BootstrapFailed]
    }
  }

  it should "handle startup failures" in {
    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory.load("application-invalid-config")
    }

    eventually[Assertion] {
      service.state shouldBe a[Service.State.StartupFailed]
    }
  }

  private def getJwt(
    subject: String,
    signatureKey: JsonWebKey
  ): Future[String] = {
    val authConfig = defaultConfig.getConfig("stasis.server.authenticators.users")

    val jwt = MockJwtGenerators.generateJwt(
      issuer = authConfig.getString("issuer"),
      audience = authConfig.getString("audience"),
      subject = subject,
      signatureKey = signatureKey
    )

    Future.successful(jwt)
  }

  private def createUser(
    serviceUrl: String,
    request: CreateUser,
    jwt: String
  )(implicit trustedContext: HttpsConnectionContext): Future[Done] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$serviceUrl/users",
          entity = request
        ).addCredentials(OAuth2BearerToken(token = jwt)),
        connectionContext = trustedContext
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, _, _) => Future.successful(Done)
        case response                              => fail(s"Unexpected response received: [$response]")
      }

  private def getUsers(
    serviceUrl: String,
    jwt: String
  )(implicit trustedContext: HttpsConnectionContext): Future[Seq[User]] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$serviceUrl/users"
        ).addCredentials(OAuth2BearerToken(token = jwt)),
        connectionContext = trustedContext
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[Seq[User]]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }

  private def createTrustedContext(): HttpsConnectionContext = {
    val config = defaultConfig.getConfig("stasis.test.server.service.context")

    val keyStore = EndpointContext.loadStore(EndpointContext.StoreConfig(config.getConfig("keystore")))

    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)

    val sslContext = SSLContext.getInstance(config.getString("protocol"))
    sslContext.init(None.orNull, factory.getTrustManagers, new SecureRandom())

    new HttpsConnectionContext(sslContext)
  }

  private val defaultConfig: Config = ConfigFactory.load()

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServiceSpec"
  )

  import scala.language.implicitConversions

  implicit def requestToEntity[T](request: T)(implicit m: Marshaller[T, RequestEntity]): RequestEntity =
    Marshal(request).to[RequestEntity].await
}

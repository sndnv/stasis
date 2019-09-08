package stasis.test.specs.unit.server.service

import java.security.SecureRandom
import java.util.UUID

import akka.Done
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import com.typesafe.config.{Config, ConfigFactory}
import javax.net.ssl.{SSLContext, TrustManagerFactory}
import org.jose4j.jwk.JsonWebKey
import org.scalatest.concurrent.Eventually
import stasis.core.security.tls.EndpointContext
import stasis.server.service.{ApiPersistence, Service}
import stasis.shared.api.requests.CreateUser
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.jwt.mocks.{MockJwksEndpoint, MockJwtsGenerators}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

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
    val serviceInterface = "localhost"
    val servicePort = 29999
    val serviceUrl = s"https://$serviceInterface:$servicePort"

    val persistence = eventually {
      service.state match {
        case Service.State.Started(persistence: ApiPersistence, _) => persistence
        case state                                                 => fail(s"Unexpected service state encountered: [$state]")
      }
    }

    val existingUser = UUID.fromString("749d8c0e-6105-4022-ae0e-39bd77752c5d")

    val createUserRequest = CreateUser(
      limits = None,
      permissions = Set(
        Permission.View.Service
      )
    )

    for {
      jwt <- getJwt(subject = existingUser.toString, signatureKey = defaultJwk)
      usersBefore <- persistence.users.view().list().map(_.values.toSeq)
      apiUsersBefore <- getUsers(serviceUrl, jwt)
      _ <- createUser(serviceUrl, createUserRequest, jwt)
      usersAfter <- persistence.users.view().list().map(_.values.toSeq)
      apiUsersAfter <- getUsers(serviceUrl, jwt)
      _ <- persistence.drop()
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
    }
  }

  it should "handle bootstrap failures" in {
    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory.load("application-invalid-bootstrap")
    }

    eventually {
      service.state shouldBe a[Service.State.BootstrapFailed]
    }
  }

  it should "handle startup failures" in {
    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory.load("application-invalid-config")
    }

    eventually {
      service.state shouldBe a[Service.State.StartupFailed]
    }
  }

  private def getJwt(
    subject: String,
    signatureKey: JsonWebKey
  )(implicit trustedContext: HttpsConnectionContext): Future[String] = {
    val authConfig = defaultConfig.getConfig("stasis.server.authenticators.user")

    val jwt = MockJwtsGenerators.generateJwt(
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
    val contextConfig = EndpointContext.Config(config)

    val keyStore = EndpointContext.loadKeyStore(contextConfig)

    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)

    val sslContext = SSLContext.getInstance(contextConfig.protocol)
    sslContext.init(None.orNull, factory.getTrustManagers, new SecureRandom())

    new HttpsConnectionContext(sslContext)
  }

  private val defaultConfig: Config = ConfigFactory.load()

  import scala.language.implicitConversions

  implicit def requestToEntity[T](request: T)(implicit m: Marshaller[T, RequestEntity]): RequestEntity =
    Marshal(request).to[RequestEntity].await
}

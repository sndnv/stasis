package stasis.server.service

import java.util.UUID

import javax.net.ssl.TrustManagerFactory

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.jose4j.jwk.JsonWebKey
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import play.api.libs.json.Json

import stasis.core.api.PoolClient
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.networking.http.HttpEndpointClient
import stasis.core.packaging.Crate
import io.github.sndnv.layers.security.mocks.MockJwtGenerator
import io.github.sndnv.layers.security.tls.EndpointContext
import stasis.server.security.mocks._
import stasis.shared.api.requests.CreateUser
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.shared.model.Generators

class ServiceSpec extends AsyncUnitSpec with ScalatestRouteTest with Eventually {
  "Service" should "handle API and metrics requests" in {
    implicit val trustedContext: EndpointContext = createTrustedContext()

    val token = "test-token"

    val identityPort = 29997
    val identityEndpoint =
      MockIdentityUserManageEndpoint(
        port = identityPort,
        credentials = OAuth2BearerToken(token)
      )

    identityEndpoint.start()

    val jwtPort = 29998
    val jwtEndpoint = new MockSimpleJwtEndpoint(jwtPort, token)

    val defaultJwk = jwtEndpoint.jwks.getJsonWebKeys.asScala.find(_.getKeyType != "oct") match {
      case Some(jwk) => jwk
      case None      => fail("Expected at least one mock JWK but none were found")
    }

    jwtEndpoint.start()

    val service = new Service {}
    val interface = "localhost"

    val servicePort = 39999
    val serviceUrl = s"https://$interface:$servicePort"

    val corePort = 49999
    val coreUrl = s"https://$interface:$corePort"

    val metricsPort = 59999
    val metricsUrl = s"http://$interface:$metricsPort/metrics"

    val (serverPersistence, corePersistence) = eventually[(ServerPersistence, CorePersistence)] {
      service.state match {
        case Service.State.Started(apiServices, coreServices) => (apiServices.persistence, coreServices.persistence)
        case state                                            => fail(s"Unexpected service state encountered: [$state]")
      }
    }

    val existingUser = UUID.fromString("749d8c0e-6105-4022-ae0e-39bd77752c5d")

    val createUserRequest = CreateUser(
      username = "test-user",
      rawPassword = "test-password",
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
      maxChunkSize = 100,
      config = PoolClient.Config.Default
    )

    for {
      jwt <- getJwt(subject = existingUser.toString, signatureKey = defaultJwk)
      usersBefore <- serverPersistence.users.view().list().map(_.toSeq)
      apiUsersBefore <- getUsers(serviceUrl, jwt)
      _ <- createUser(serviceUrl, createUserRequest, jwt)
      usersAfter <- serverPersistence.users.view().list().map(_.toSeq)
      apiUsersAfter <- getUsers(serviceUrl, jwt)
      crateDiscarded <- coreClient.discard(address = coreAddress, crate = Crate.generateId())
      metrics <- getMetrics(metricsUrl)
      _ <- serverPersistence.drop()
      _ <- corePersistence.drop()
      _ = service.stop()
      _ = jwtEndpoint.stop()
      _ = identityEndpoint.stop()
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

      metrics.filter(_.startsWith(Service.Telemetry.Instrumentation)) should not be empty
      metrics.filter(_.startsWith("jvm")) should not be empty
      metrics.filter(_.startsWith("process")) should not be empty
    }
  }

  it should "handle device bootstrap requests" in {
    implicit val trustedContext: EndpointContext = createTrustedContext()

    val token = "test-token"

    val jwtPort = 28998
    val jwtEndpoint = new MockSimpleJwtEndpoint(jwtPort, token)

    val defaultJwk = jwtEndpoint.jwks.getJsonWebKeys.asScala.find(_.getKeyType != "oct") match {
      case Some(jwk) => jwk
      case None      => fail("Expected at least one mock JWK but none were found")
    }

    jwtEndpoint.start()

    val identityPort = 28997
    val identityEndpoint =
      MockIdentityDeviceManageEndpoint(
        port = identityPort,
        credentials = OAuth2BearerToken(token),
        existingDevice = None
      )

    identityEndpoint.start()

    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory.load("application-device-bootstrap")
    }
    val interface = "localhost"

    val bootstrapPort = 48999
    val bootstrapUrl = s"https://$interface:$bootstrapPort"

    val (serverPersistence, corePersistence) = eventually[(ServerPersistence, CorePersistence)] {
      service.state match {
        case Service.State.Started(apiServices, coreServices) => (apiServices.persistence, coreServices.persistence)
        case state                                            => fail(s"Unexpected service state encountered: [$state]")
      }
    }

    val existingUser = UUID.fromString("749d8c0e-6105-4022-ae0e-39bd77752c5d")

    val existingDevice = Generators.generateDevice.copy(owner = existingUser)
    serverPersistence.devices.manage().put(existingDevice).await

    for {
      jwt <- getJwt(subject = existingUser.toString, signatureKey = defaultJwk)
      code <- getBootstrapCode(bootstrapUrl, existingDevice.id, jwt)
      bootstrapCodeExistsBeforeExec <- serverPersistence.deviceBootstrapCodes.view().get(code.value).map(_.isDefined)
      params <- getBootstrapParams(bootstrapUrl, code.value)
      bootstrapCodeExistsAfterExec <- serverPersistence.deviceBootstrapCodes.view().get(code.value).map(_.isDefined)
      _ <- serverPersistence.drop()
      _ <- corePersistence.drop()
      _ = service.stop()
      _ = jwtEndpoint.stop()
      _ = identityEndpoint.stop()
    } yield {
      identityEndpoint.searched should be(1)
      identityEndpoint.created should be(1)
      identityEndpoint.updated should be(0)

      bootstrapCodeExistsBeforeExec should be(true)
      code.owner should be(existingUser)
      code.target should be(Left(existingDevice.id))

      bootstrapCodeExistsAfterExec should be(false)
      params.authentication.tokenEndpoint should be("http://localhost:28998/oauth/token")
      params.authentication.clientId should not be empty
      params.authentication.clientSecret should not be empty
      params.authentication.useQueryString should be(false)
      params.authentication.scopes.api should be("urn:stasis:identity:audience:stasis-server-test")
      params.authentication.scopes.core should be("urn:stasis:identity:audience:stasis-server-test")
      params.authentication.context.enabled should be(true)
      params.authentication.context.protocol should be("TLS")
      params.serverApi.url should be("https://localhost:28999")
      params.serverApi.user should be(existingUser.toString)
      params.serverApi.userSalt should be("drzi1ZLxHq5MZ67M")
      params.serverApi.device should be(existingDevice.id.toString)
      params.serverApi.context.enabled should be(false)
      params.serverApi.context.protocol should be("TLS")
      params.serverCore.address should be("https://localhost:38999")
      params.serverCore.nodeId should be(existingDevice.node.toString)
      params.serverCore.context.enabled should be(false)
      params.serverCore.context.protocol should be("TLS")
      params.additionalConfig should be(Json.parse("""{"a":{"b":{"c":"d","e":1,"f":["g","h"]}}}"""))
    }
  }

  it should "pause the service after bootstrap is complete (mode=init)" in {
    implicit val trustedContext: EndpointContext = createTrustedContext()

    val interface = "localhost"
    val servicePort = ports.dequeue()
    val serviceUrl = s"https://$interface:$servicePort"

    val corePort = ports.dequeue()
    val coreUrl = s"https://$interface:$corePort"

    val bootstrapPort = ports.dequeue()
    val metricsPort = ports.dequeue()

    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory
        .load()
        .withValue("stasis.server.service.bootstrap.mode", ConfigValueFactory.fromAnyRef("init"))
        .withValue("stasis.server.service.api.port", ConfigValueFactory.fromAnyRef(servicePort))
        .withValue("stasis.server.service.core.port", ConfigValueFactory.fromAnyRef(servicePort))
        .withValue("stasis.server.service.telemetry.metrics.port", ConfigValueFactory.fromAnyRef(metricsPort))
        .withValue("stasis.server.bootstrap.api.port", ConfigValueFactory.fromAnyRef(bootstrapPort))
    }

    eventually[Assertion] {
      service.state should be(Service.State.BootstrapComplete)
    }

    for {
      serviceFailure <- makeBasicRequest(serviceUrl).failed
      coreFailure <- makeBasicRequest(coreUrl).failed
    } yield {
      serviceFailure.getMessage should include("Connection refused")
      coreFailure.getMessage should include("Connection refused")
    }
  }

  it should "pause the service after bootstrap is complete (mode=drop)" in {
    implicit val trustedContext: EndpointContext = createTrustedContext()

    val interface = "localhost"
    val servicePort = ports.dequeue()
    val serviceUrl = s"https://$interface:$servicePort"

    val corePort = ports.dequeue()
    val coreUrl = s"https://$interface:$corePort"

    val bootstrapPort = ports.dequeue()

    val metricsPort = ports.dequeue()

    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory
        .load()
        .withValue("stasis.server.service.bootstrap.mode", ConfigValueFactory.fromAnyRef("drop"))
        .withValue("stasis.server.service.api.port", ConfigValueFactory.fromAnyRef(servicePort))
        .withValue("stasis.server.service.core.port", ConfigValueFactory.fromAnyRef(servicePort))
        .withValue("stasis.server.service.telemetry.metrics.port", ConfigValueFactory.fromAnyRef(metricsPort))
        .withValue("stasis.server.bootstrap.api.port", ConfigValueFactory.fromAnyRef(bootstrapPort))
    }

    eventually[Assertion] {
      service.state should be(Service.State.BootstrapComplete)
    }

    for {
      serviceFailure <- makeBasicRequest(serviceUrl).failed
      coreFailure <- makeBasicRequest(coreUrl).failed
    } yield {
      serviceFailure.getMessage should include("Connection refused")
      coreFailure.getMessage should include("Connection refused")
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

  private def makeBasicRequest(url: String)(implicit trustedContext: EndpointContext): Future[Done] =
    Http()
      .singleRequest(
        request = HttpRequest(method = HttpMethods.GET, uri = url),
        connectionContext = trustedContext.connection
      )
      .map(_ => Done)

  private def getJwt(
    subject: String,
    signatureKey: JsonWebKey
  ): Future[String] = {
    val authConfig = defaultConfig.getConfig("stasis.server.authenticators.users")

    val jwt = MockJwtGenerator.generateJwt(
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
  )(implicit trustedContext: EndpointContext): Future[Done] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$serviceUrl/v1/users",
          entity = request
        ).addCredentials(OAuth2BearerToken(token = jwt)),
        connectionContext = trustedContext.connection
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, _, _) => Future.successful(Done)
        case response                              => fail(s"Unexpected response received: [$response]")
      }
  }

  private def getUsers(
    serviceUrl: String,
    jwt: String
  )(implicit trustedContext: EndpointContext): Future[Seq[User]] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$serviceUrl/v1/users"
        ).addCredentials(OAuth2BearerToken(token = jwt)),
        connectionContext = trustedContext.connection
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[Seq[User]]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }
  }

  private def getBootstrapCode(
    bootstrapUrl: String,
    device: Device.Id,
    jwt: String
  )(implicit trustedContext: EndpointContext): Future[DeviceBootstrapCode] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$bootstrapUrl/v1/devices/codes/own/for-device/${device.toString}"
        ).addCredentials(OAuth2BearerToken(token = jwt)),
        connectionContext = trustedContext.connection
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[DeviceBootstrapCode]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }
  }

  private def getBootstrapParams(
    bootstrapUrl: String,
    code: String
  )(implicit trustedContext: EndpointContext): Future[DeviceBootstrapParameters] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$bootstrapUrl/v1/devices/execute"
        ).addCredentials(OAuth2BearerToken(token = code)),
        connectionContext = trustedContext.connection
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[DeviceBootstrapParameters]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }
  }

  private def getMetrics(
    metricsUrl: String
  ): Future[Seq[String]] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = metricsUrl
        )
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[String]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }
      .map { result =>
        result.split("\n").toSeq.filterNot(_.startsWith("#"))
      }

  private def createTrustedContext(): EndpointContext = {
    val config = defaultConfig.getConfig("stasis.test.server.service.context")
    val storeConfig = EndpointContext.StoreConfig(config.getConfig("keystore"))

    val keyStore = EndpointContext.loadStore(storeConfig)

    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)

    EndpointContext(
      config = EndpointContext.Config(protocol = config.getString("protocol"), storeConfig = Right(storeConfig)),
      keyManagers = None,
      trustManagers = Option(factory.getTrustManagers)
    )
  }

  private val defaultConfig: Config = ConfigFactory.load()

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ServiceSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(15.seconds, 250.milliseconds)

  import scala.language.implicitConversions

  implicit def requestToEntity[T](request: T)(implicit m: Marshaller[T, RequestEntity]): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private val ports: mutable.Queue[Int] = (42000 to 42100).to(mutable.Queue)
}

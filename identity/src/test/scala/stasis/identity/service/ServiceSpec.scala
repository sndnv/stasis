package stasis.identity.service

import java.security.SecureRandom

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.ConnectionContext
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.HttpsConnectionContext
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.util.ByteString
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jws.JsonWebSignature
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import play.api.libs.json._

import stasis.identity.RouteTest
import stasis.identity.api.manage.requests.CreateApi
import stasis.identity.api.manage.requests.CreateOwner
import stasis.identity.model.apis.Api
import io.github.sndnv.layers.security.tls.EndpointContext

class ServiceSpec extends RouteTest with Eventually {
  import ServiceSpec._

  "Identity Service" should "authenticate and authorize actions, and provide metrics" in {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.identity.api.Formats._

    implicit val clientContext: HttpsConnectionContext = createTrustedContext()

    val service = new Service {}
    val serviceInterface = "localhost"
    val servicePort = 19999
    val serviceUrl = s"https://$serviceInterface:$servicePort"

    val metricsPort = 29999
    val metricsUrl = s"http://$serviceInterface:$metricsPort/metrics"

    val persistence = eventually[Persistence] {
      service.state match {
        case Service.State.Started(persistence: Persistence, _) => persistence
        case state                                              => fail(s"Unexpected service state encountered: [$state]")
      }
    }

    val owner = ResourceOwnerCredentials(
      username = "existing-user",
      password = "existing-user-password",
      scope = s"urn:stasis:identity:audience:${Api.ManageIdentity}"
    )

    val client = ClientCredentials(
      username = getEntity(persistence.clients.all).id.toString,
      password = "existing-client-secret"
    )

    val newApi = "new-api"
    val newUser = "new-user"
    val newUserPassword = "new-user-password"
    val newUserScope = "urn:stasis:identity:audience:new-api"

    for {
      accessToken <- getAccessToken(
        serviceUrl = serviceUrl,
        owner = owner,
        client = client
      )
      _ <- createEntity(
        serviceUrl = serviceUrl,
        entities = "apis",
        request = CreateApi(id = newApi),
        accessToken = accessToken
      )
      _ <- createEntity(
        serviceUrl = serviceUrl,
        entities = "owners",
        request = CreateOwner(
          username = newUser,
          rawPassword = newUserPassword,
          allowedScopes = Seq(newUserScope),
          subject = Some("some-subject")
        ),
        accessToken = accessToken
      )
      newAccessToken <- getAccessToken(
        serviceUrl = serviceUrl,
        owner = ResourceOwnerCredentials(username = newUser, password = newUserPassword, scope = newUserScope),
        client = client
      )
      apis <- persistence.apis.all
      owners <- persistence.resourceOwners.all
      clients <- persistence.clients.all
      signatureKey <- getJwk(serviceUrl)
      metrics <- getMetrics(metricsUrl)
      _ <- persistence.drop()
      _ = service.stop()
    } yield {
      apis.size should be(3)
      owners.size should be(2)
      clients.size should be(1)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(newAccessToken)
      jws.setKey(signatureKey.getKey)

      jws.verifySignature() should be(true)

      metrics.filter(_.startsWith(Service.Telemetry.Instrumentation)) should not be empty
      metrics.filter(_.startsWith("jvm")) should not be empty
      metrics.filter(_.startsWith("process")) should not be empty
    }
  }

  it should "pause the service after bootstrap is complete (mode=init)" in {
    val serviceInterface = "localhost"
    val servicePort = ports.dequeue()
    val serviceUrl = s"https://$serviceInterface:$servicePort"

    val metricsPort = ports.dequeue()

    implicit val clientContext: HttpsConnectionContext = createTrustedContext()

    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory
        .load()
        .withValue("stasis.identity.bootstrap.mode", ConfigValueFactory.fromAnyRef("init"))
        .withValue("stasis.identity.service.api.port", ConfigValueFactory.fromAnyRef(servicePort))
        .withValue("stasis.identity.service.telemetry.metrics.port", ConfigValueFactory.fromAnyRef(metricsPort))
    }

    eventually[Assertion] {
      service.state should be(Service.State.BootstrapComplete)
    }

    for {
      apiFailure <- getJwk(serviceUrl).failed
    } yield {
      apiFailure.getMessage should include("Connection refused")
    }
  }

  it should "pause the service after bootstrap is complete (mode=drop)" in {
    val serviceInterface = "localhost"
    val servicePort = ports.dequeue()
    val serviceUrl = s"https://$serviceInterface:$servicePort"

    val metricsPort = ports.dequeue()

    implicit val clientContext: HttpsConnectionContext = createTrustedContext()

    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory
        .load()
        .withValue("stasis.identity.bootstrap.mode", ConfigValueFactory.fromAnyRef("drop"))
        .withValue("stasis.identity.service.api.port", ConfigValueFactory.fromAnyRef(servicePort))
        .withValue("stasis.identity.service.telemetry.metrics.port", ConfigValueFactory.fromAnyRef(metricsPort))
    }

    eventually[Assertion] {
      service.state should be(Service.State.BootstrapComplete)
    }

    for {
      apiFailure <- getJwk(serviceUrl).failed
    } yield {
      apiFailure.getMessage should include("Connection refused")
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

  private def getAccessToken(
    serviceUrl: String,
    owner: ResourceOwnerCredentials,
    client: ClientCredentials
  )(implicit clientContext: HttpsConnectionContext): Future[String] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = s"$serviceUrl/oauth/token" +
              s"?grant_type=password" +
              s"&username=${owner.username}" +
              s"&password=${owner.password}" +
              s"&scope=${owner.scope}"
          ).addCredentials(BasicHttpCredentials(client.username, client.password)),
          connectionContext = clientContext
        )
      entity <- response.entity.dataBytes.runFold(ByteString.empty)(_ concat _)
    } yield {
      (Json.parse(entity.utf8String).as[JsObject] \ "access_token").as[String]
    }

  private def createEntity(
    serviceUrl: String,
    entities: String,
    request: RequestEntity,
    accessToken: String
  )(implicit clientContext: HttpsConnectionContext): Future[Done] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$serviceUrl/manage/$entities",
          entity = request
        ).addCredentials(OAuth2BearerToken(token = accessToken)),
        connectionContext = clientContext
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, _, _) => Future.successful(Done)
        case response                              => fail(s"Unexpected response received: [$response]")
      }

  private def getJwk(
    serviceUrl: String
  )(implicit clientContext: HttpsConnectionContext): Future[JsonWebKey] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$serviceUrl/jwks/jwks.json"
          ),
          connectionContext = clientContext
        )
      entity <- response.entity.dataBytes.runFold(ByteString.empty)(_ concat _)
    } yield {
      new JsonWebKeySet(entity.utf8String).getJsonWebKeys.asScala.toList match {
        case jwk :: Nil => jwk
        case _          => fail(s"Unexpected JWKs response received: [$entity]")
      }
    }

  private def getEntity[T](list: => Future[Seq[T]]): T =
    list.await.headOption match {
      case Some(entity) => entity
      case None         => fail("Existing entity expected but none was found")
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

  private def createTrustedContext(): HttpsConnectionContext = {
    val config = ConfigFactory.load().getConfig("stasis.test.identity.service.context")
    val storeConfig = EndpointContext.StoreConfig(config.getConfig("keystore"))

    val keyStore = EndpointContext.loadStore(storeConfig)

    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)

    val sslContext = SSLContext.getInstance(config.getString("protocol"))
    sslContext.init(None.orNull, factory.getTrustManagers, new SecureRandom())

    ConnectionContext.httpsClient(sslContext)
  }

  private val ports: mutable.Queue[Int] = (41000 to 41100).to(mutable.Queue)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(15.seconds, 250.milliseconds)
}

object ServiceSpec {
  private final case class ResourceOwnerCredentials(username: String, password: String, scope: String)
  private final case class ClientCredentials(username: String, password: String)
}

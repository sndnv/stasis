package stasis.test.specs.unit.identity.service

import java.security.SecureRandom

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import javax.net.ssl.{SSLContext, TrustManagerFactory}
import stasis.identity.service.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec

import scala.collection.mutable

class EndpointContextSpec extends AsyncUnitSpec {
  private val ports: mutable.Queue[Int] = (25000 to 25100).to[mutable.Queue]

  "An EndpointContext" should "load its config" in {
    val expectedConfig = EndpointContext.Config(
      keystorePath = "./identity/src/test/resources/certs/localhost.p12",
      keystoreType = "PKCS12",
      keystorePassword = "",
      protocol = "TLS"
    )

    val actualConfig = EndpointContext.Config(config.getConfig("service.context"))

    actualConfig should be(expectedConfig)
  }

  it should "create an SSL context" in {
    val interface = "localhost"
    val port = ports.dequeue()
    val contextConfig = EndpointContext.Config(config.getConfig("service.context"))
    val context = EndpointContext.create(contextConfig)

    implicit val system: ActorSystem = ActorSystem(name = "EndpointContextSpec")
    implicit val mat: ActorMaterializer = ActorMaterializer()

    val endpointUrl = s"https://$interface:$port"

    val _ = Http().bindAndHandle(
      handler = get { Directives.complete(StatusCodes.OK) },
      interface = interface,
      port = port,
      connectionContext = context
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = endpointUrl
        ),
        connectionContext = createTrustedContext()
      )
      .map {
        case HttpResponse(status, _, _, _) => status should be(StatusCodes.OK)
        case response                      => fail(s"Unexpected response received: [$response]")
      }
  }

  private val config: Config = ConfigFactory.load().getConfig("stasis.test.identity")

  private def createTrustedContext(): HttpsConnectionContext = {
    val contextConfig = EndpointContext.Config(config.getConfig("service.context"))

    val keyStore = EndpointContext.loadKeyStore(contextConfig)

    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)

    val sslContext = SSLContext.getInstance(contextConfig.protocol)
    sslContext.init(None.orNull, factory.getTrustManagers, new SecureRandom())

    new HttpsConnectionContext(sslContext)
  }
}

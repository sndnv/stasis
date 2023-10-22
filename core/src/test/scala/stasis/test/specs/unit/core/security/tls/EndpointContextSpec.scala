package stasis.test.specs.unit.core.security.tls

import java.io.FileNotFoundException

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.server.Directives._
import com.typesafe.config.{Config, ConfigFactory}
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec

import scala.collection.mutable

class EndpointContextSpec extends AsyncUnitSpec {
  "An EndpointContext" should "load store config" in {
    val expectedConfig = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val actualConfig = EndpointContext.StoreConfig(config.getConfig("context-server.keystore"))

    actualConfig should be(expectedConfig)
  }

  it should "load context config" in {
    val expectedServerConfig = EndpointContext.Config(
      protocol = "TLS",
      storeConfig = Left(
        EndpointContext.StoreConfig(
          storePath = "./core/src/test/resources/certs/localhost.p12",
          storeType = "PKCS12",
          storePassword = ""
        )
      )
    )

    val actualServerConfig = EndpointContext.Config(
      config = config.getConfig("context-server")
    )

    actualServerConfig should be(expectedServerConfig)

    val expectedClientConfig = EndpointContext.Config(
      protocol = "TLS",
      storeConfig = Right(
        EndpointContext.StoreConfig(
          storePath = "./core/src/test/resources/certs/localhost.p12",
          storeType = "PKCS12",
          storePassword = ""
        )
      )
    )

    val actualClientConfig = EndpointContext.Config(
      config = config.getConfig("context-client")
    )

    actualClientConfig should be(expectedClientConfig)
  }

  it should "create connection contexts (server/client)" in {
    val interface = "localhost"
    val port = ports.dequeue()

    val serverContext = EndpointContext(
      config = EndpointContext.Config(
        config = config.getConfig("context-server")
      )
    )

    val clientContext = EndpointContext(
      config = EndpointContext.Config(
        config = config.getConfig("context-client")
      )
    )

    implicit val system: ActorSystem = ActorSystem(name = "EndpointContextSpec")

    val endpointUrl = s"https://$interface:$port"

    val _ = Http()
      .newServerAt(interface = interface, port = port)
      .enableHttps(serverContext.connection)
      .bindFlow(
        handlerFlow = get {
          Directives.complete(StatusCodes.OK)
        }
      )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = endpointUrl
        ),
        connectionContext = clientContext.connection
      )
      .map {
        case HttpResponse(status, _, _, _) => status should be(StatusCodes.OK)
        case response                      => fail(s"Unexpected response received: [$response]")
      }
  }

  it should "fail if a store is missing" in {
    val expectedMessage = "Store [./core/src/test/resources/certs/missing.p12] with type [JKS] was not found"

    val actualException =
      intercept[FileNotFoundException] {
        EndpointContext(config = config.getConfig("context-missing"))
      }

    actualException.getMessage should be(expectedMessage)
  }

  it should "not create contexts if not enabled" in {
    EndpointContext(config = config.getConfig("context-disabled")) should be(None)
  }

  private val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")

  private val ports: mutable.Queue[Int] = (25000 to 25100).to(mutable.Queue)
}

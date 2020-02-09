package stasis.test.specs.unit.core.security.tls

import java.io.FileNotFoundException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec

import scala.collection.mutable

class EndpointContextSpec extends AsyncUnitSpec {
  private val ports: mutable.Queue[Int] = (25000 to 25100).to[mutable.Queue]

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
    val expectedServerConfig = EndpointContext.ContextConfig(
      protocol = "TLS",
      keyStoreConfig = Some(
        EndpointContext.StoreConfig(
          storePath = "./core/src/test/resources/certs/localhost.p12",
          storeType = "PKCS12",
          storePassword = ""
        )
      ),
      trustStoreConfig = None
    )

    val actualServerConfig = EndpointContext.ContextConfig(
      config = config.getConfig("context-server")
    )

    actualServerConfig should be(expectedServerConfig)
    actualServerConfig.requireClientAuth should be(false)

    val expectedClientConfig = EndpointContext.ContextConfig(
      protocol = "TLS",
      keyStoreConfig = None,
      trustStoreConfig = Some(
        EndpointContext.StoreConfig(
          storePath = "./core/src/test/resources/certs/localhost.p12",
          storeType = "PKCS12",
          storePassword = ""
        )
      )
    )

    val actualClientConfig = EndpointContext.ContextConfig(
      config = config.getConfig("context-client")
    )

    actualClientConfig should be(expectedClientConfig)
    actualClientConfig.requireClientAuth should be(false)

    val expectedMutualConfig = EndpointContext.ContextConfig(
      protocol = "TLS",
      keyStoreConfig = Some(
        EndpointContext.StoreConfig(
          storePath = "./core/src/test/resources/certs/localhost.p12",
          storeType = "PKCS12",
          storePassword = ""
        )
      ),
      trustStoreConfig = Some(
        EndpointContext.StoreConfig(
          storePath = "./core/src/test/resources/certs/localhost.p12",
          storeType = "PKCS12",
          storePassword = ""
        )
      )
    )

    val actualMutualConfig = EndpointContext.ContextConfig(
      config = config.getConfig("context-mutual")
    )

    actualMutualConfig should be(expectedMutualConfig)
    actualMutualConfig.requireClientAuth should be(true)
  }

  it should "create connection contexts (server/client)" in {
    val interface = "localhost"
    val port = ports.dequeue()

    val serverContext = EndpointContext.create(
      contextConfig = EndpointContext.ContextConfig(
        config = config.getConfig("context-server")
      )
    )

    val clientContext = EndpointContext.create(
      contextConfig = EndpointContext.ContextConfig(
        config = config.getConfig("context-client")
      )
    )

    implicit val system: ActorSystem = ActorSystem(name = "EndpointContextSpec")
    implicit val mat: ActorMaterializer = ActorMaterializer()

    val endpointUrl = s"https://$interface:$port"

    val _ = Http().bindAndHandle(
      handler = get { Directives.complete(StatusCodes.OK) },
      interface = interface,
      port = port,
      connectionContext = serverContext
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = endpointUrl
        ),
        connectionContext = clientContext
      )
      .map {
        case HttpResponse(status, _, _, _) => status should be(StatusCodes.OK)
        case response                      => fail(s"Unexpected response received: [$response]")
      }
  }

  it should "create connection contexts (mutual)" in {
    val interface = "localhost"
    val port = ports.dequeue()

    val serverContext = EndpointContext.fromConfig(config = config.getConfig("context-mutual")).get
    val clientContext = EndpointContext.fromConfig(config = config.getConfig("context-mutual")).get

    implicit val system: ActorSystem = ActorSystem(name = "EndpointContextSpec")
    implicit val mat: ActorMaterializer = ActorMaterializer()

    val endpointUrl = s"https://$interface:$port"

    val _ = Http().bindAndHandle(
      handler = get { Directives.complete(StatusCodes.OK) },
      interface = interface,
      port = port,
      connectionContext = serverContext
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = endpointUrl
        ),
        connectionContext = clientContext
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
        EndpointContext.fromConfig(config = config.getConfig("context-missing"))
      }

    actualException.getMessage should be(expectedMessage)
  }

  it should "not create contexts if not enabled" in {
    EndpointContext.fromConfig(config = config.getConfig("context-disabled")) should be(None)
  }

  private val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")
}

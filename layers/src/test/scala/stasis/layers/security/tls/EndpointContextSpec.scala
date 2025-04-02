package stasis.layers.security.tls

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

import scala.collection.mutable
import scala.jdk.CollectionConverters._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.util.ByteString

import stasis.layers.UnitSpec

class EndpointContextSpec extends UnitSpec {
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

  it should "support encoding and decoding a KeyStore to/from ByteString without private keys" in {
    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val password = "test-password"

    val original = EndpointContext.loadStore(config)

    val encoded = EndpointContext.Encoded.encodeKeyStore(
      store = original,
      password = password,
      storeType = config.storeType
    )

    val decoded = EndpointContext.Encoded.decodeKeyStore(
      content = encoded,
      password = password,
      storeType = config.storeType
    )

    val encodedWithCertsOnly = EndpointContext.Encoded.encodeKeyStore(
      store = decoded,
      password = password,
      storeType = config.storeType
    )

    val decodedWithCertsOnly = EndpointContext.Encoded.decodeKeyStore(
      content = encodedWithCertsOnly,
      password = password,
      storeType = config.storeType
    )

    original.size should be > 0
    original.aliases().asScala.toList.exists(original.isKeyEntry)

    decoded.size should be > 0
    decoded.aliases().asScala.toList.forall(decoded.isCertificateEntry) should be(true)

    decodedWithCertsOnly.size should be > 0
    decodedWithCertsOnly.aliases().asScala.toList.forall(decodedWithCertsOnly.isCertificateEntry) should be(true)
  }

  it should "fail to decode a KeyStore with private keys" in {
    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val password = "test-password"

    val original = EndpointContext.loadStore(config)

    val content = new ByteArrayOutputStream()
    original.store(content, password.toCharArray)

    val encoded = ByteString.fromArray(content.toByteArray)

    an[IllegalArgumentException] should be thrownBy {
      EndpointContext.Encoded.decodeKeyStore(encoded, password, config.storeType)
    }
  }

  it should "support creating enabled contexts" in {
    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val actualContext = EndpointContext.Encoded.enabled(protocol = "TLS", config = config)

    actualContext.enabled should be(true)
    actualContext.protocol should be("TLS")
    actualContext.storeType should be(config.storeType)
    actualContext.temporaryStorePassword.length should be(EndpointContext.Encoded.TemporaryPasswordSize)
    actualContext.storeContent should not be empty
  }

  it should "support creating disabled contexts" in {
    EndpointContext.Encoded.disabled() should be(
      EndpointContext.Encoded(
        enabled = false,
        protocol = "TLS",
        storeType = "PKCS12",
        temporaryStorePassword = "",
        storeContent = ByteString.empty
      )
    )
  }

  it should "support creating contexts from config" in {
    val enabledContext = EndpointContext.Encoded(config.getConfig("context-enabled"))

    enabledContext.enabled should be(true)
    enabledContext.protocol should be("TLS")
    enabledContext.storeType should be("PKCS12")
    enabledContext.temporaryStorePassword.length should be(EndpointContext.Encoded.TemporaryPasswordSize)
    enabledContext.storeContent should not be empty

    val disabledContext = EndpointContext.Encoded(config.getConfig("context-disabled"))

    disabledContext.enabled should be(false)
    disabledContext.protocol should be(EndpointContext.Encoded.DefaultProtocol)
    disabledContext.storeType should be(EndpointContext.Encoded.DefaultStoreType)
    disabledContext.temporaryStorePassword should be(empty)
    disabledContext.storeContent should be(empty)
  }

  private val config: Config = ConfigFactory.load().getConfig("stasis.test.layers.security.tls")

  private val ports: mutable.Queue[Int] = (25000 to 25100).to(mutable.Queue)
}

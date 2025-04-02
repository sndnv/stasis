package stasis.test.specs.unit.core.discovery.http

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials

import stasis.core.discovery.ServiceDiscoveryClient
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.discovery.exceptions.DiscoveryFailure
import stasis.core.discovery.http.HttpServiceDiscoveryClient
import stasis.layers.UnitSpec
import stasis.layers.security.tls.EndpointContext
import stasis.test.specs.unit.core.discovery.http.HttpServiceDiscoveryClientSpec.TestAttributes
import stasis.test.specs.unit.core.discovery.mocks.MockDiscoveryApiEndpoint

class HttpServiceDiscoveryClientSpec extends UnitSpec {
  "A HttpServiceDiscoveryClient" should "support retrieving latest service discovery information" in {
    val apiPort = ports.dequeue()
    val api = new MockDiscoveryApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = createClient(apiPort)

    apiClient.latest(isInitialRequest = false).map { result =>
      result should be(ServiceDiscoveryResult.KeepExisting)
    }
  }

  it should "handle unexpected responses" in {
    import stasis.core.api.Formats._
    import stasis.core.discovery.http.HttpServiceDiscoveryClient._

    val response = HttpResponse(status = StatusCodes.OK, entity = "unexpected-response-entity")

    response
      .to[ServiceDiscoveryResult]
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: DiscoveryFailure) =>
        e.getMessage should be(
          "Discovery API request unmarshalling failed with: [Unsupported Content-Type [Some(text/plain; charset=UTF-8)], supported: application/json]"
        )
      }
  }

  it should "handle endpoint failures" in {
    import stasis.core.api.Formats._
    import stasis.core.discovery.http.HttpServiceDiscoveryClient._

    val status = StatusCodes.NotFound
    val message = "Test Failure"
    val response = HttpResponse(status = status, entity = message)

    response
      .to[ServiceDiscoveryResult]
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: DiscoveryFailure) =>
        e.getMessage should be(s"Discovery API request failed with [$status]: [$message]")
      }
  }

  private def createClient(
    apiPort: Int,
    context: Option[EndpointContext] = None
  ): HttpServiceDiscoveryClient = {
    val client = HttpServiceDiscoveryClient(
      apiUrl = context match {
        case Some(_) => s"https://localhost:$apiPort"
        case None    => s"http://localhost:$apiPort"
      },
      credentials = Future.successful(apiCredentials),
      attributes = TestAttributes(a = "test-attribute"),
      context = context
    )

    client
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "HttpServiceDiscoveryClientSpec"
  )

  private val ports: mutable.Queue[Int] = (43000 to 43100).to(mutable.Queue)

  private val apiCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
}

object HttpServiceDiscoveryClientSpec {
  final case class TestAttributes(a: String) extends ServiceDiscoveryClient.Attributes
}

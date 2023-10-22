package stasis.test.specs.unit.core.api

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.stream.QueueOfferResult
import com.typesafe.config.{Config, ConfigFactory}
import stasis.core.api.PoolClient
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.api.PoolClientSpec.TestClient
import stasis.test.specs.unit.core.security.mocks.MockJwksEndpoint

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

class PoolClientSpec extends AsyncUnitSpec {
  "A PoolClient" should "support making HTTP requests" in {
    val endpoint = new MockJwksEndpoint(port = ports.dequeue())
    endpoint.start()

    val client = new TestClient(context = None)

    client
      .makeRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"${endpoint.url}/valid/jwks.json"
        )
      )
      .map { response =>
        endpoint.stop()

        response.status should be(StatusCodes.OK)
      }
  }

  it should "support custom connection contexts" in {
    val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")

    val serverContextConfig = EndpointContext.Config(config.getConfig("context-server-jks"))

    val clientContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-client"))
    )

    val endpoint = new MockJwksEndpoint(
      port = ports.dequeue(),
      withKeystoreConfig = serverContextConfig.keyStoreConfig
    )

    endpoint.start()

    val client = new TestClient(context = Some(clientContext))

    client
      .makeRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"${endpoint.url}/valid/jwks.json"
        )
      )
      .map { response =>
        endpoint.stop()

        response.status should be(StatusCodes.OK)
      }
  }

  it should "process http pool offer results" in {
    val request = HttpRequest()
    val promise = Promise.successful(Done)
    val failure = new RuntimeException("test failure")

    val process: QueueOfferResult => Future[Done] = PoolClient.processOfferResult(request = request, promise = promise)

    for {
      enqueued <- process(QueueOfferResult.Enqueued)
      dropped <- process(QueueOfferResult.Dropped).failed
      failed <- process(QueueOfferResult.Failure(failure)).failed
      closed <- process(QueueOfferResult.QueueClosed).failed
    } yield {
      enqueued should be(Done)
      dropped.getMessage should be("[GET] request for endpoint [/] failed; dropped by stream")
      failed.getMessage should be(s"[GET] request for endpoint [/] failed; RuntimeException: ${failure.getMessage}")
      closed.getMessage should be("[GET] request for endpoint [/] failed; stream closed")
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "PoolClientSpec"
  )

  private val ports: mutable.Queue[Int] = (36000 to 36100).to(mutable.Queue)
}

object PoolClientSpec {
  class TestClient(
    override protected val context: Option[EndpointContext]
  )(implicit override protected val system: ActorSystem[SpawnProtocol.Command])
      extends PoolClient {
    override protected def requestBufferSize: Int = 100

    def makeRequest(request: HttpRequest): Future[HttpResponse] = offer(request)
  }
}

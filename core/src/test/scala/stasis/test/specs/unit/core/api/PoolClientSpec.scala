package stasis.test.specs.unit.core.api

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SpawnProtocol
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.stream.QueueOfferResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import stasis.core.api.PoolClient
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.api.PoolClientSpec.TestClient
import stasis.test.specs.unit.core.security.mocks.MockJwksEndpoint

class PoolClientSpec extends AsyncUnitSpec {
  "A PoolClient" should "support checking for retryable requests/responses" in {
    // 1xx
    PoolClient.canRetry(status = StatusCodes.Continue) should be(false)

    // 2xx
    PoolClient.canRetry(status = StatusCodes.OK) should be(false)
    PoolClient.canRetry(status = StatusCodes.Created) should be(false)
    PoolClient.canRetry(status = StatusCodes.Accepted) should be(false)
    PoolClient.canRetry(status = StatusCodes.NoContent) should be(false)

    // 3xx
    PoolClient.canRetry(status = StatusCodes.Found) should be(false)
    PoolClient.canRetry(status = StatusCodes.TemporaryRedirect) should be(false)
    PoolClient.canRetry(status = StatusCodes.PermanentRedirect) should be(false)

    // 4xx
    PoolClient.canRetry(status = StatusCodes.BadRequest) should be(false)
    PoolClient.canRetry(status = StatusCodes.Unauthorized) should be(false)
    PoolClient.canRetry(status = StatusCodes.Forbidden) should be(false)
    PoolClient.canRetry(status = StatusCodes.NotFound) should be(false)
    PoolClient.canRetry(status = StatusCodes.MethodNotAllowed) should be(false)
    PoolClient.canRetry(status = StatusCodes.NotAcceptable) should be(false)
    PoolClient.canRetry(status = StatusCodes.RequestTimeout) should be(true)
    PoolClient.canRetry(status = StatusCodes.FailedDependency) should be(true)
    PoolClient.canRetry(status = StatusCodes.TooEarly) should be(true)
    PoolClient.canRetry(status = StatusCodes.TooManyRequests) should be(true)

    // 5xx
    PoolClient.canRetry(status = StatusCodes.InternalServerError) should be(true)
    PoolClient.canRetry(status = StatusCodes.NotImplemented) should be(false)
    PoolClient.canRetry(status = StatusCodes.BadGateway) should be(true)
    PoolClient.canRetry(status = StatusCodes.ServiceUnavailable) should be(true)
    PoolClient.canRetry(status = StatusCodes.GatewayTimeout) should be(true)
    PoolClient.canRetry(status = StatusCodes.BandwidthLimitExceeded) should be(true)
    PoolClient.canRetry(status = StatusCodes.NetworkReadTimeout) should be(true)
    PoolClient.canRetry(status = StatusCodes.NetworkConnectTimeout) should be(true)
  }

  it should "support making HTTP requests" in {
    val endpoint = new MockJwksEndpoint(port = ports.dequeue())
    endpoint.start()

    val client = new TestClient(context = None)

    val jwksPath = "/valid/jwks.json"

    client
      .makeRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"${endpoint.url}$jwksPath"
        )
      )
      .map { response =>
        endpoint.stop()
        endpoint.count(path = jwksPath) should be(1)

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

    val jwksPath = "/valid/jwks.json"

    client
      .makeRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"${endpoint.url}$jwksPath"
        )
      )
      .map { response =>
        endpoint.stop()
        endpoint.count(path = jwksPath) should be(1)

        response.status should be(StatusCodes.OK)
      }
  }

  it should "support retrying failed http requests" in {
    val endpoint = new MockJwksEndpoint(port = ports.dequeue())
    endpoint.start()

    val client = new TestClient(context = None)

    val jwksPath = "/invalid/jwks.json"

    client
      .makeRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"${endpoint.url}$jwksPath"
        )
      )
      .map { response =>
        endpoint.stop()
        endpoint.count(path = jwksPath) should be(6)

        response.status should be(StatusCodes.InternalServerError)
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
    override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    override protected def config: PoolClient.Config = PoolClient.Config.Default.copy(
      minBackoff = 10.millis,
      maxBackoff = 50.millis
    )

    def makeRequest(request: HttpRequest): Future[HttpResponse] = offer(request)
  }
}

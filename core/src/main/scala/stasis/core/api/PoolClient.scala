package stasis.core.api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.SpawnProtocol
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.HttpsConnectionContext
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCode
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.scaladsl.RetryFlow
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.slf4j.Logger
import stasis.core.networking.exceptions.ClientFailure
import stasis.core.security.tls.EndpointContext
import stasis.core.streaming.Operators.ExtendedSource

trait PoolClient {
  protected implicit def system: ActorSystem[SpawnProtocol.Command]
  protected def log: Logger

  protected def context: Option[EndpointContext]
  protected def config: PoolClient.Config = PoolClient.Config.Default

  private implicit val ec: ExecutionContext = system.executionContext

  private val http = Http()

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context.connection
    case None          => http.defaultClientHttpsContext
  }

  private val pool = http.superPool[Promise[HttpResponse]](connectionContext = clientContext)

  private val queue = Source
    .queue(
      bufferSize = 0,
      overflowStrategy = OverflowStrategy.backpressure,
      maxConcurrentOffers = config.requestBufferSize
    )
    .via(
      RetryFlow.withBackoff(
        minBackoff = config.minBackoff,
        maxBackoff = config.maxBackoff,
        randomFactor = config.randomFactor,
        maxRetries = config.maxRetries,
        flow = pool
      ) {
        case ((request, promise), (Success(response), _)) if PoolClient.canRetry(response.status) =>
          response.entity.dataBytes.cancelled()

          log.warnN(
            "Retrying request for [{}:{}]; received unexpected response: [{}]",
            request.uri.authority.host,
            request.uri.authority.port,
            response.status.value
          )

          Some((request, promise))

        case _ => None
      }
    )
    .to(
      Sink.foreach { case (response, promise) =>
        val _ = promise.complete(response)
      }
    )
    .run()

  protected def offer(request: HttpRequest): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]()

    queue
      .offer((request, promise))
      .flatMap(PoolClient.processOfferResult(request, promise))
  }
}

object PoolClient {
  def processOfferResult[T](request: HttpRequest, promise: Promise[T])(result: QueueOfferResult): Future[T] = {
    def clientFailure(cause: String): Future[T] =
      Future.failed(
        ClientFailure(
          message = s"[${request.method.value}] request for endpoint [${request.uri.toString}] failed; $cause"
        )
      )

    result match {
      case QueueOfferResult.Enqueued    => promise.future
      case QueueOfferResult.Dropped     => clientFailure(cause = "dropped by stream")
      case QueueOfferResult.Failure(e)  => clientFailure(cause = s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case QueueOfferResult.QueueClosed => clientFailure(cause = "stream closed")
    }
  }

  def canRetry(status: StatusCode): Boolean =
    status match {
      // 4xx
      case StatusCodes.RequestTimeout  => true
      case StatusCodes.TooEarly        => true
      case StatusCodes.TooManyRequests => true

      // 5xx
      case StatusCodes.InternalServerError    => true
      case StatusCodes.BadGateway             => true
      case StatusCodes.ServiceUnavailable     => true
      case StatusCodes.GatewayTimeout         => true
      case StatusCodes.BandwidthLimitExceeded => true
      case StatusCodes.NetworkReadTimeout     => true
      case StatusCodes.NetworkConnectTimeout  => true

      // other
      case _ => false
    }

  final case class Config(
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    randomFactor: Double,
    maxRetries: Int,
    requestBufferSize: Int
  )

  object Config {
    final val Default: Config = Config(
      minBackoff = 500.millis,
      maxBackoff = 3.seconds,
      randomFactor = 0.1d,
      maxRetries = 5,
      requestBufferSize = 1000
    )
  }
}

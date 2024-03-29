package stasis.core.api

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.{Http, HttpsConnectionContext}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.stream.{OverflowStrategy, QueueOfferResult}
import stasis.core.networking.exceptions.ClientFailure
import stasis.core.security.tls.EndpointContext

import scala.concurrent.{ExecutionContext, Future, Promise}

trait PoolClient {
  protected implicit def system: ActorSystem[SpawnProtocol.Command]
  protected def context: Option[EndpointContext]
  protected def requestBufferSize: Int

  private implicit val ec: ExecutionContext = system.executionContext

  private val http = Http()

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context.connection
    case None          => http.defaultClientHttpsContext
  }

  private val queue = Source
    .queue(
      bufferSize = requestBufferSize,
      overflowStrategy = OverflowStrategy.backpressure
    )
    .via(http.superPool[Promise[HttpResponse]](connectionContext = clientContext))
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
}

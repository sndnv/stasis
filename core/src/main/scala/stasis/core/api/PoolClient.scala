package stasis.core.api

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import stasis.core.networking.exceptions.ClientFailure

import scala.concurrent.{ExecutionContext, Future, Promise}

trait PoolClient {
  protected implicit def system: ActorSystem[SpawnProtocol.Command]
  protected def context: Option[HttpsConnectionContext]
  protected def requestBufferSize: Int

  private implicit val ec: ExecutionContext = system.executionContext

  private val http = Http()(system.classicSystem)

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context
    case None          => http.defaultClientHttpsContext
  }

  private val queue = Source
    .queue(
      bufferSize = requestBufferSize,
      overflowStrategy = OverflowStrategy.backpressure
    )
    .via(http.superPool[Promise[HttpResponse]](connectionContext = clientContext))
    .to(
      Sink.foreach {
        case (response, promise) => val _ = promise.complete(response)
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

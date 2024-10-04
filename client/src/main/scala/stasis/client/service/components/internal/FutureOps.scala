package stasis.client.service.components.internal

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

trait FutureOps {
  implicit def ec: ExecutionContext

  implicit class OpToFuture[T](op: => T) {
    def future: Future[T] = Future.fromTry(Try(op))
  }

  implicit class TryOpToFuture[T](op: => Try[T]) {
    def future: Future[T] = Future.fromTry(op)
  }

  implicit class FutureOpWithTransformedFailures[T](op: => Future[T]) {
    def transformFailureTo(transformer: Throwable => Throwable): Future[T] =
      op.recoverWith { case NonFatal(e) => Future.failed(transformer(e)) }
  }
}

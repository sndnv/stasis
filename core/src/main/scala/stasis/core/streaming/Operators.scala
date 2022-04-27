package stasis.core.streaming

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{Future, Promise}

object Operators {
  implicit class ExtendedSource[+Out, +Mat](source: Source[Out, Mat]) {
    def cancelled()(implicit mat: Materializer): Future[Done] = {
      import mat.executionContext

      val promise = Promise[Done]()

      val _ = source
        .watchTermination[Mat]() { case (prevMatValue, result) =>
          val _ = promise.completeWith(
            result.recover {
              case e: IllegalStateException if e.getMessage.contains("cannot be materialized more than once") =>
                // ignore MaterializedTwiceException
                Done
            }
          )

          prevMatValue
        }
        .runWith(Sink.cancelled[Out])

      promise.future
    }

    def ignored()(implicit mat: Materializer): Future[Done] =
      source.runWith(Sink.ignore)
  }
}

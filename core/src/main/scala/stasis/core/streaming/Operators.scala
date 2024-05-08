package stasis.core.streaming

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString

import scala.concurrent.{Future, Promise}

object Operators {
  implicit class ExtendedSource[+Out, +Mat](source: Source[Out, Mat]) {

    /**
      * Materializes the stream and cancels it, without processing elements.
      * <br/><br/>
      * Note: Some elements may still be consumed and processed, depending
      * on the definition/behaviour of the source.
      *
      * @return future that completes when the stream terminates
      */
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

    /**
      * Materializes the stream and consumes all elements by ignoring them.
      *
      * @return future that completes when the stream terminates
      */
    def ignored()(implicit mat: Materializer): Future[Done] =
      source.runWith(Sink.ignore)

    /**
      * Drops elements that have already been sent downstream.
      * <br/><br/>
      * Note: Only checks against the last emitted element, if any. For example:
      * <pre>
      *
      *  val result = Source(List("a", "b", "b", "c", "b"))
      *                 .dropDuplicates(f = Option.apply)
      *                 .runWith(Sink.seq)
      *                 .await
      *
      *  result == Seq("a", "b", "c", "b")
      * </pre>
      *
      * @return
      */
    def dropLatestDuplicates[T >: Null](f: Out => Option[T]): Source[T, Mat] =
      source
        .statefulMap[Option[T], Option[T]](create = () => None)(
          f = (last, element) =>
            f(element) match {
              case current if last == current => (last, None)
              case current                    => (current, current)
            },
          onComplete = _ => None
        )
        .mapConcat(_.toList)
  }

  implicit class ExtendedByteStringSource[+Mat](source: Source[ByteString, Mat]) {
    def repartition(withMaximumElementSize: Int): Source[ByteString, Mat] =
      source
        .mapConcat { current =>
          if (current.size > withMaximumElementSize) {
            current.grouped(size = withMaximumElementSize).toList
          } else {
            List(current)
          }
        }
  }
}

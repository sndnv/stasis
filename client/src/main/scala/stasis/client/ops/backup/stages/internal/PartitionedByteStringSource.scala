package stasis.client.ops.backup.stages.internal

import org.apache.pekko.stream.scaladsl.{Source, SubFlow}
import org.apache.pekko.stream.{ActorAttributes, IOResult, Supervision}
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class PartitionedByteStringSource(val source: Source[ByteString, Future[IOResult]]) {
  def partition(withMaximumPartSize: Long): SubFlow[ByteString, Future[IOResult], source.Repr, source.Closed] =
    source
      .statefulMapConcat { () =>
        var collected: Long = 0

        { current =>
          val currentSize = current.length

          require(
            withMaximumPartSize >= currentSize,
            s"Stream element size [${currentSize.toString}] is above maximum part size [${withMaximumPartSize.toString}]"
          )

          if (collected + currentSize > withMaximumPartSize) {
            collected = currentSize.toLong
            (current, true) :: Nil
          } else {
            collected += currentSize
            (current, false) :: Nil
          }
        }
      }
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
      .splitWhen(_._2)
      .map(_._1)
}

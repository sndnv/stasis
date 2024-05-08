package stasis.client.ops.backup.stages.internal

import org.apache.pekko.stream.scaladsl.{Source, SubFlow}
import org.apache.pekko.stream.{ActorAttributes, IOResult, Supervision}
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

class PartitionedByteStringSource(val source: Source[ByteString, Future[IOResult]]) {
  def partition(withMaximumPartSize: Long): SubFlow[ByteString, Future[IOResult], source.Repr, source.Closed] =
    source
      .statefulMap[Long, (ByteString, Boolean)](create = () => 0L)(
        f = (collected, current) => {
          val currentSize = current.length

          require(
            withMaximumPartSize >= currentSize,
            s"Stream element size [${currentSize.toString}] is above maximum part size [${withMaximumPartSize.toString}]"
          )

          if (collected + currentSize > withMaximumPartSize) {
            (currentSize.toLong, (current, true))
          } else {
            (collected + currentSize, (current, false))
          }
        },
        onComplete = _ => None
      )
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
      .splitWhen(_._2)
      .map(_._1)
}

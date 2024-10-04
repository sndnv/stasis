package stasis.client.ops.backup.stages.internal

import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.SubFlow
import org.apache.pekko.stream.ActorAttributes
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Supervision
import org.apache.pekko.util.ByteString

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

package stasis.test.specs.unit.client.ops.backup.stages.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import stasis.client.ops.backup.stages.internal.PartitionedByteStringSource
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.Future
import scala.util.control.NonFatal

class PartitionedByteStringSourceSpec extends AsyncUnitSpec {
  "A PartitionedByteStringSource" should "support data stream partitioning" in {
    val original = Source("o" :: "r" :: "i" :: "g" :: "i" :: "n" :: "a" :: "l" :: Nil)
      .map(ByteString.apply)
      .mapMaterializedValue(_ => Future.successful(IOResult.createSuccessful(0)))

    val extended = new PartitionedByteStringSource(original)

    val subFlowCounter = new AtomicInteger(0)
    val subFlowEntries = new ConcurrentHashMap[Int, ByteString]()

    val flow = Flow.lazyFutureFlow(() => {
      val subFlowId = subFlowCounter.getAndIncrement()
      Future.successful(
        Flow[ByteString]
          .map { entry: ByteString =>
            subFlowEntries.compute(
              subFlowId,
              (_, existing) => Option(existing).getOrElse(ByteString.empty).concat(entry)
            )
          }
      )
    })

    extended
      .partition(withMaximumPartSize = 3)
      .via(flow)
      .mergeSubstreams
      .runWith(Sink.seq)
      .map { _ =>
        subFlowCounter.get should be(3)
        subFlowEntries.get(0) should be(ByteString("ori"))
        subFlowEntries.get(1) should be(ByteString("gin"))
        subFlowEntries.get(2) should be(ByteString("al"))
      }
  }

  it should "fail if a stream element is larger than the maximum part size" in {
    val element = "original"
    val maxSize = 3

    val original = Source(element :: Nil)
      .map(ByteString.apply)
      .mapMaterializedValue(_ => Future.successful(IOResult.createSuccessful(0)))

    val extended = new PartitionedByteStringSource(original)

    extended
      .partition(withMaximumPartSize = maxSize)
      .mergeSubstreams
      .runWith(Sink.seq)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recoverWith {
        case NonFatal(e: IllegalArgumentException) =>
          e.getMessage should be(
            s"requirement failed: Stream element size [${element.length}] is above maximum part size [$maxSize]"
          )
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "PartitionedByteStringSourceSpec")
}

package stasis.layers.streaming

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.layers.UnitSpec

class OperatorsSpec extends UnitSpec {
  "Operators" should "support terminating a stream without consuming its data (cancelled)" in {
    import stasis.layers.streaming.Operators.ExtendedSource

    val counter = new AtomicInteger(0)

    val successfulSource = Source("part-0" :: "part-1" :: "part-2" :: Nil).map { bytes =>
      val _ = counter.incrementAndGet()
      bytes
    }

    val failedSource = Source.repeat(Done).map { _ =>
      throw new RuntimeException("Test failure")
    }

    val failedSourceWithMultipleMaterializations = Source.repeat(Done).map { _ =>
      throw new IllegalStateException("Substream Source(Test) cannot be materialized more than once")
    }

    successfulSource.cancelled().map { _ =>
      counter.get() should be(0)
    }

    failedSource.cancelled().failed.map { e =>
      e shouldBe an[RuntimeException]
      e.getMessage should be("Test failure")
    }

    failedSourceWithMultipleMaterializations.cancelled().map { _ =>
      counter.get() should be(0)
    }
  }

  they should "support terminating a stream by consuming its data (ignored)" in {
    import stasis.layers.streaming.Operators.ExtendedSource

    val counter = new AtomicInteger(0)

    val source = Source("part-0" :: "part-1" :: "part-2" :: Nil).map { bytes =>
      val _ = counter.incrementAndGet()
      bytes
    }

    source.ignored().map { _ =>
      counter.get() should be(3)
    }
  }

  they should "drop duplicates" in {
    import stasis.layers.streaming.Operators.ExtendedSource

    val source = Source(List("a", "b", "a", "a", "a", "b", "b", "b", "c", "d", "c", "e"))

    source
      .dropLatestDuplicates(Option.apply)
      .runWith(Sink.seq)
      .await should be(
      Seq(
        "a",
        "b",
        "a",
        "b",
        "c",
        "d",
        "c",
        "e"
      )
    )
  }

  they should "support repartitioning individual stream elements" in {
    import stasis.layers.streaming.Operators.ExtendedByteStringSource

    val originalElements = new AtomicInteger(0)

    val source = Source("abcdef" :: "g1234" :: "5678" :: "9" :: Nil).map { bytes =>
      val _ = originalElements.incrementAndGet()
      ByteString.fromString(bytes)
    }

    source
      .repartition(withMaximumElementSize = 4)
      .runFold(Seq.empty[ByteString])(_ :+ _)
      .map { partitionedElements =>
        originalElements.get() should be(4)
        partitionedElements.map(_.utf8String) should be(Seq("abcd", "ef", "g123", "4", "5678", "9"))
      }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "OperatorsSpec"
  )
}

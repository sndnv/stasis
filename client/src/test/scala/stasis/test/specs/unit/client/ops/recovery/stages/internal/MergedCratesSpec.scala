package stasis.test.specs.unit.client.ops.recovery.stages.internal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import stasis.client.ops.recovery.stages.internal.MergedCrates
import stasis.test.specs.unit.AsyncUnitSpec

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import scala.util.control.NonFatal

class MergedCratesSpec extends AsyncUnitSpec {
  "MergedCrates" should "support data stream merging (single crate)" in {
    val original = Seq(
      (0, Paths.get("/tmp/file/one__part=0"), Source.single(ByteString("original_1")))
    )

    val extended = new MergedCrates(original)

    val partsProcessed = new AtomicInteger(0)

    extended
      .merge(onPartProcessed = () => partsProcessed.incrementAndGet())
      .runWith(Sink.seq)
      .map { merged =>
        partsProcessed.get() should be(1)

        merged.toList match {
          case part1 :: Nil =>
            part1 should be(ByteString("original_1"))

          case result =>
            fail(s"Unexpected result received: [$result]")
        }
      }
  }

  it should "support data stream merging (multiple crates)" in {
    val original = Seq(
      (0, Paths.get("/tmp/file/one__part=0"), Source.single(ByteString("original_1"))),
      (2, Paths.get("/tmp/file/one__part=2"), Source.single(ByteString("original_3"))),
      (1, Paths.get("/tmp/file/one__part=1"), Source.single(ByteString("original_2")))
    )

    val extended = new MergedCrates(original)

    val partsProcessed = new AtomicInteger(0)

    extended
      .merge(onPartProcessed = () => partsProcessed.incrementAndGet())
      .runWith(Sink.seq)
      .map { merged =>
        partsProcessed.get() should be(3)

        merged.toList match {
          case part1 :: part2 :: part3 :: Nil =>
            part1 should be(ByteString("original_1"))
            part2 should be(ByteString("original_2"))
            part3 should be(ByteString("original_3"))

          case result =>
            fail(s"Unexpected result received: [$result]")
        }
      }
  }

  it should "fail if no crates are provided" in {
    val extended = new MergedCrates(Seq.empty)

    val partsProcessed = new AtomicInteger(0)

    extended
      .merge(onPartProcessed = () => partsProcessed.incrementAndGet())
      .runWith(Sink.seq)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recoverWith { case NonFatal(e) =>
        partsProcessed.get() should be(0)
        e.getMessage should be("Expected at least one crate but none were found")
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MergedCratesSpec")
}

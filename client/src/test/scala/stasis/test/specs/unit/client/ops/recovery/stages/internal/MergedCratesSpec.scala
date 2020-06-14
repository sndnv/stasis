package stasis.test.specs.unit.client.ops.recovery.stages.internal

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import stasis.client.ops.exceptions.EntityProcessingFailure
import stasis.client.ops.recovery.stages.internal.MergedCrates
import stasis.test.specs.unit.AsyncUnitSpec

import scala.util.control.NonFatal

class MergedCratesSpec extends AsyncUnitSpec {
  "MergedCrates" should "support data stream merging (single crate)" in {
    val original = Seq(
      (Paths.get("/tmp/file/one_0"), Source.single(ByteString("original_1")))
    )

    val extended = new MergedCrates(original)

    extended
      .merge()
      .runWith(Sink.seq)
      .map { merged =>
        merged.length should be(1)
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
      (Paths.get("/tmp/file/one_0"), Source.single(ByteString("original_1"))),
      (Paths.get("/tmp/file/one_2"), Source.single(ByteString("original_3"))),
      (Paths.get("/tmp/file/one_1"), Source.single(ByteString("original_2")))
    )

    val extended = new MergedCrates(original)

    extended
      .merge()
      .runWith(Sink.seq)
      .map { merged =>
        merged.length should be(3)
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

    extended
      .merge()
      .runWith(Sink.seq)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recoverWith {
        case NonFatal(e: EntityProcessingFailure) =>
          e.getMessage should be("Expected at least one crate but none were found")
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MergedCratesSpec")
}

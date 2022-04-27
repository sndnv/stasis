package stasis.test.specs.unit.core.streaming

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Source
import stasis.test.specs.unit.AsyncUnitSpec

import java.util.concurrent.atomic.AtomicInteger

class OperatorsSpec extends AsyncUnitSpec {
  "Operators" should "support terminating a stream without consuming its data (cancelled)" in {
    import stasis.core.streaming.Operators.ExtendedSource

    val counter = new AtomicInteger(0)

    val successfulSource = Source("part-0" :: "part-1" :: "part-2" :: Nil).map { bytes =>
      counter.incrementAndGet()
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
    import stasis.core.streaming.Operators.ExtendedSource

    val counter = new AtomicInteger(0)

    val source = Source("part-0" :: "part-1" :: "part-2" :: Nil).map { bytes =>
      counter.incrementAndGet()
      bytes
    }

    source.ignored().map { _ =>
      counter.get() should be(3)
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "OperatorsSpec"
  )
}

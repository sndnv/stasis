package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import stasis.client.compression.{Decoder, Encoder}
import stasis.test.specs.unit.client.mocks.MockCompression.Statistic

class MockCompression() extends Encoder with Decoder {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.Compressed -> new AtomicInteger(0),
    Statistic.Decompressed -> new AtomicInteger(0)
  )

  override def compress: Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .map { _ =>
        stats(Statistic.Compressed).incrementAndGet()
        ByteString("compressed")
      }

  override def decompress: Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .map { _ =>
        stats(Statistic.Decompressed).incrementAndGet()
        ByteString("decompressed")
      }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockCompression {
  sealed trait Statistic
  object Statistic {
    case object Compressed extends Statistic
    case object Decompressed extends Statistic
  }
}

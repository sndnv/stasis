package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

import stasis.client.compression.Compression
import stasis.client.compression.Decoder
import stasis.client.compression.Encoder
import stasis.client.model.SourceEntity
import stasis.client.model.TargetEntity
import stasis.test.specs.unit.client.mocks.MockCompression.Statistic

class MockCompression() extends Compression with Encoder with Decoder {
  override val name: String = "mock"

  override val defaultCompression: Encoder with Decoder = this

  override val disabledExtensions: Set[String] = Set("test")

  override def algorithmFor(entity: Path): String = "mock"
  override def encoderFor(entity: SourceEntity): Encoder = this
  override def decoderFor(entity: TargetEntity): Decoder = this

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
  def apply(): MockCompression = new MockCompression()

  sealed trait Statistic
  object Statistic {
    case object Compressed extends Statistic
    case object Decompressed extends Statistic
  }
}

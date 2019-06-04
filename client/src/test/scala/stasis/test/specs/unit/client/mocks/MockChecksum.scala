package stasis.test.specs.unit.client.mocks

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import akka.stream.Materializer
import stasis.client.analysis.Checksum
import stasis.test.specs.unit.client.mocks.MockChecksum.Statistic

import scala.concurrent.Future

class MockChecksum(checksums: Map[Path, BigInt]) extends Checksum {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.ChecksumCalculated -> new AtomicInteger(0)
  )

  override def calculate(file: Path)(implicit mat: Materializer): Future[BigInt] = {
    stats(Statistic.ChecksumCalculated).incrementAndGet()
    checksums.get(file) match {
      case Some(checksum) =>
        Future.successful(checksum)

      case None =>
        Future.failed(new IllegalArgumentException(s"No checksum found for file [$file]"))
    }
  }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockChecksum {
  sealed trait Statistic
  object Statistic {
    case object ChecksumCalculated extends Statistic
  }
}

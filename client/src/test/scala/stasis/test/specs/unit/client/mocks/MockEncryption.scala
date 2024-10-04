package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

import stasis.client.encryption.Decoder
import stasis.client.encryption.Encoder
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.test.specs.unit.client.mocks.MockEncryption.Statistic

class MockEncryption() extends Encoder with Decoder {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.FileEncrypted -> new AtomicInteger(0),
    Statistic.MetadataEncrypted -> new AtomicInteger(0),
    Statistic.FileDecrypted -> new AtomicInteger(0),
    Statistic.MetadataDecrypted -> new AtomicInteger(0)
  )

  override def encrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .map { _ =>
        stats(Statistic.FileEncrypted).incrementAndGet()
        ByteString("file-encrypted")
      }

  override def encrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .map { _ =>
        stats(Statistic.MetadataEncrypted).incrementAndGet()
        ByteString("metadata-encrypted")
      }

  override def maxPlaintextSize: Long = 16 * 1024

  override def decrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .map { _ =>
        stats(Statistic.FileDecrypted).incrementAndGet()
        ByteString("file-decrypted")
      }

  override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .map { _ =>
        stats(Statistic.MetadataDecrypted).incrementAndGet()
        ByteString("metadata-decrypted")
      }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockEncryption {
  sealed trait Statistic
  object Statistic {
    case object FileEncrypted extends Statistic
    case object MetadataEncrypted extends Statistic
    case object FileDecrypted extends Statistic
    case object MetadataDecrypted extends Statistic
  }
}

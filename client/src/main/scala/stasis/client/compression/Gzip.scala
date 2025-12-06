package stasis.client.compression

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.{Compression => PekkoCompression}
import org.apache.pekko.util.ByteString

object Gzip extends Encoder with Decoder {
  override val name: String = "gzip"
  override def compress: Flow[ByteString, ByteString, NotUsed] = PekkoCompression.gzip
  override def decompress: Flow[ByteString, ByteString, NotUsed] = PekkoCompression.gzipDecompress()
}

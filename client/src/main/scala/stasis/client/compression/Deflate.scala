package stasis.client.compression

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Compression => PekkoCompression, Flow}
import org.apache.pekko.util.ByteString

object Deflate extends Encoder with Decoder {
  override val name: String = "deflate"
  override def compress: Flow[ByteString, ByteString, NotUsed] = PekkoCompression.deflate
  override def decompress: Flow[ByteString, ByteString, NotUsed] = PekkoCompression.inflate()
}

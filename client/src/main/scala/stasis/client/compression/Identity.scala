package stasis.client.compression

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

object Identity extends Encoder with Decoder {
  override val name: String = "none"
  override def compress: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
  override def decompress: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
}

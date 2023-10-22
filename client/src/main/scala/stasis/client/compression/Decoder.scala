package stasis.client.compression

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

trait Decoder {
  def name: String
  def decompress: Flow[ByteString, ByteString, NotUsed]
}

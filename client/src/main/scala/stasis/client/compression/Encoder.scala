package stasis.client.compression

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

trait Encoder {
  def name: String
  def compress: Flow[ByteString, ByteString, NotUsed]
}

package stasis.client.compression

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

trait Decoder {
  def decompress: Flow[ByteString, ByteString, NotUsed]
}

package stasis.client.compression

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

trait Encoder {
  def name: String
  def compress: Flow[ByteString, ByteString, NotUsed]
}

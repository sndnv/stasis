package stasis.client.compression

import akka.NotUsed
import akka.stream.scaladsl.{Compression, Flow}
import akka.util.ByteString

class Gzip extends Encoder with Decoder {
  override def compress: Flow[ByteString, ByteString, NotUsed] = Compression.gzip
  override def decompress: Flow[ByteString, ByteString, NotUsed] = Compression.gunzip()
}
package stasis.client.ops.recovery.stages.internal

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.compression.Decoder

class DecompressedByteStringSource(val source: Source[ByteString, NotUsed]) {
  def decompress(decompressor: Decoder): source.Repr[ByteString] =
    source.via(decompressor.decompress)
}

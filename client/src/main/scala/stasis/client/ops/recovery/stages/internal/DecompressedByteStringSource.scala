package stasis.client.ops.recovery.stages.internal

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.compression.Decoder

class DecompressedByteStringSource(val source: Source[ByteString, NotUsed]) {
  def decompress(decompressor: Decoder): source.Repr[ByteString] =
    source.via(decompressor.decompress)
}

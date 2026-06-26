package stasis.client.ops.backup.stages.internal

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.compression.Encoder

class CompressedByteStringSource(val source: Source[ByteString, NotUsed]) {
  def compress(compressor: Encoder): source.Repr[ByteString] =
    source.via(compressor.compress)
}

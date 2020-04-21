package stasis.client.ops.recovery.stages.internal

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.ops.recovery.Providers

class DecompressedByteStringSource(val source: Source[ByteString, NotUsed]) {
  def decompress()(implicit providers: Providers): source.Repr[ByteString] =
    source.via(providers.decompressor.decompress)
}

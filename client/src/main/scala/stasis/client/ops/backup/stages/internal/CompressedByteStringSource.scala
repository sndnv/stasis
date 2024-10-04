package stasis.client.ops.backup.stages.internal

import scala.concurrent.Future

import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.compression.Encoder

class CompressedByteStringSource(val source: Source[ByteString, Future[IOResult]]) {
  def compress(compressor: Encoder): source.Repr[ByteString] =
    source.via(compressor.compress)
}

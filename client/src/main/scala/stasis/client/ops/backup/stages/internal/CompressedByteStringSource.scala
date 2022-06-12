package stasis.client.ops.backup.stages.internal

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.compression.Encoder

import scala.concurrent.Future

class CompressedByteStringSource(val source: Source[ByteString, Future[IOResult]]) {
  def compress(compressor: Encoder): source.Repr[ByteString] =
    source.via(compressor.compress)
}

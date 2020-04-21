package stasis.client.ops.backup.stages.internal

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.ops.backup.Providers

import scala.concurrent.Future

class CompressedByteStringSource(val source: Source[ByteString, Future[IOResult]]) {
  def compress()(implicit providers: Providers): source.Repr[ByteString] =
    source.via(providers.compressor.compress)
}

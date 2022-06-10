package stasis.client.ops.recovery.stages.internal

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.ops.Metrics
import stasis.client.ops.recovery.Providers

class DecompressedByteStringSource(val source: Source[ByteString, NotUsed]) {
  def decompress()(implicit providers: Providers): source.Repr[ByteString] = {
    val metrics = providers.telemetry.metrics[Metrics.RecoveryOperation]
    source
      .via(providers.decompressor.decompress)
      .wireTap(bytes => metrics.recordEntityChunkProcessed(step = "decompressed", bytes = bytes.length))
  }
}

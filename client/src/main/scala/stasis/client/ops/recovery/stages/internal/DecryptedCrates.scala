package stasis.client.ops.recovery.stages.internal

import java.nio.file.Path
import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.ops.Metrics
import stasis.client.ops.recovery.Providers

class DecryptedCrates(crates: Iterable[(Int, Path, Source[ByteString, NotUsed])]) {
  def decrypt(
    withPartSecret: Path => DeviceFileSecret
  )(implicit providers: Providers): Iterable[(Int, Path, Source[ByteString, NotUsed])] = {
    val metrics = providers.telemetry.metrics[Metrics.RecoveryOperation]

    crates.map { case (partId, partPath, source) =>
      val decryptedSource = source
        .wireTap(bytes => metrics.recordEntityChunkProcessed(step = "pulled", bytes = bytes.length))
        .via(providers.decryptor.decrypt(withPartSecret(partPath)))
        .wireTap(bytes => metrics.recordEntityChunkProcessed(step = "decrypted", bytes = bytes.length))

      (partId, partPath, decryptedSource)
    }
  }
}

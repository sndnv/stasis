package stasis.client.ops.recovery.stages.internal

import java.nio.file.Path
import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.ops.recovery.Providers

class DecryptedCrates(crates: Iterable[(Path, Source[ByteString, NotUsed])]) {
  def decrypt(
    withPartSecret: Path => DeviceFileSecret
  )(implicit providers: Providers): Iterable[(Path, Source[ByteString, NotUsed])] =
    crates.map { case (partPath, source) =>
      val decryptedSource = source
        .via(providers.decryptor.decrypt(withPartSecret(partPath)))

      (partPath, decryptedSource)
    }
}

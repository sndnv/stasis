package stasis.client.ops.recovery

import stasis.client.collection.RecoveryCollector
import stasis.client.{compression, encryption}
import stasis.client.staging.FileStaging

final case class Providers(
  collector: RecoveryCollector,
  staging: FileStaging,
  decompressor: compression.Decoder,
  decryptor: encryption.Decoder
)

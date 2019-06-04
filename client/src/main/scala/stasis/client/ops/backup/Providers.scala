package stasis.client.ops.backup

import stasis.client.collection.BackupCollector
import stasis.client.{compression, encryption}
import stasis.client.staging.FileStaging

final case class Providers(
  collector: BackupCollector,
  staging: FileStaging,
  compressor: compression.Encoder,
  encryptor: encryption.Encoder
)

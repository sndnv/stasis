package stasis.client.ops.recovery

import stasis.client.analysis.Checksum
import stasis.client.staging.FileStaging
import stasis.client.tracking.RecoveryTracker
import stasis.client.{compression, encryption}
import stasis.client.api.clients.Clients

final case class Providers(
  checksum: Checksum,
  staging: FileStaging,
  decompressor: compression.Decoder,
  decryptor: encryption.Decoder,
  clients: Clients,
  track: RecoveryTracker
)

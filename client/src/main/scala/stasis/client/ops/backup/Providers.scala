package stasis.client.ops.backup

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.staging.FileStaging
import stasis.client.tracking.BackupTracker
import stasis.client.{compression, encryption}
import stasis.core.telemetry.TelemetryContext

final case class Providers(
  checksum: Checksum,
  staging: FileStaging,
  compressor: compression.Encoder,
  encryptor: encryption.Encoder,
  decryptor: encryption.Decoder,
  clients: Clients,
  track: BackupTracker,
  telemetry: TelemetryContext
)

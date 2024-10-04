package stasis.client.ops.backup

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.compression.Compression
import stasis.client.encryption
import stasis.client.staging.FileStaging
import stasis.client.tracking.BackupTracker
import stasis.layers.telemetry.TelemetryContext

final case class Providers(
  checksum: Checksum,
  staging: FileStaging,
  compression: Compression,
  encryptor: encryption.Encoder,
  decryptor: encryption.Decoder,
  clients: Clients,
  track: BackupTracker,
  telemetry: TelemetryContext
)

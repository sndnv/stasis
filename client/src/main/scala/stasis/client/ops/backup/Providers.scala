package stasis.client.ops.backup

import java.nio.file.FileSystem

import io.github.sndnv.layers.telemetry.TelemetryContext

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.compression.Compression
import stasis.client.encryption
import stasis.client.staging.FileStaging
import stasis.client.tracking.BackupTracker

final case class Providers(
  checksum: Checksum,
  staging: FileStaging,
  compression: Compression,
  encryptor: encryption.Encoder,
  decryptor: encryption.Decoder,
  clients: Clients,
  track: BackupTracker,
  telemetry: TelemetryContext,
  filesystem: FileSystem
)

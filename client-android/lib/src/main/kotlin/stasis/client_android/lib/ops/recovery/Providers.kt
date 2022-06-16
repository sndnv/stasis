package stasis.client_android.lib.ops.recovery

import stasis.client_android.lib.encryption.Decoder as EncryptionDecoder
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.staging.FileStaging
import stasis.client_android.lib.tracking.RecoveryTracker

data class Providers(
    val checksum: Checksum,
    val staging: FileStaging,
    val compression: Compression,
    val decryptor: EncryptionDecoder,
    val clients: Clients,
    val track: RecoveryTracker
)

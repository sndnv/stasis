package stasis.client_android.lib.ops.recovery

import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.staging.FileStaging
import stasis.client_android.lib.tracking.RecoveryTracker
import stasis.client_android.lib.compression.Decoder as CompressionDecoder
import stasis.client_android.lib.encryption.Decoder as EncryptionDecoder

data class Providers(
    val checksum: Checksum,
    val staging: FileStaging,
    val decompressor: CompressionDecoder,
    val decryptor: EncryptionDecoder,
    val clients: Clients,
    val track: RecoveryTracker
)

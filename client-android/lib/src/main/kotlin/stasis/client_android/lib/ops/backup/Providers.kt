package stasis.client_android.lib.ops.backup

import stasis.client_android.lib.encryption.Decoder as EncryptionDecoder
import stasis.client_android.lib.encryption.Encoder as EncryptionEncoder
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.staging.FileStaging
import stasis.client_android.lib.tracking.BackupTracker

data class Providers(
    val checksum: Checksum,
    val staging: FileStaging,
    val compression: Compression,
    val encryptor: EncryptionEncoder,
    val decryptor: EncryptionDecoder,
    val clients: Clients,
    val track: BackupTracker
)

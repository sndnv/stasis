package stasis.client_android.lib.encryption

import okio.Source
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret

interface Decoder {
    fun decrypt(source: Source, fileSecret: DeviceFileSecret): Source
    fun decrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source
}

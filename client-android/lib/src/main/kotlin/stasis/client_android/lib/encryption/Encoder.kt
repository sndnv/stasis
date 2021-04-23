package stasis.client_android.lib.encryption

import okio.Sink
import okio.Source
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret

interface Encoder {
    fun encrypt(source: Source, fileSecret: DeviceFileSecret): Source
    fun encrypt(sink: Sink, fileSecret: DeviceFileSecret): Sink
    fun encrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source
    val maxPlaintextSize: Long
}

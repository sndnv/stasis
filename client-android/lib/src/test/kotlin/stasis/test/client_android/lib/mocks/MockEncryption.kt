package stasis.test.client_android.lib.mocks

import okio.Buffer
import okio.Sink
import okio.Source
import stasis.client_android.lib.encryption.Decoder
import stasis.client_android.lib.encryption.Encoder
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import java.util.concurrent.atomic.AtomicInteger

open class MockEncryption : Encoder, Decoder {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.FileEncrypted to AtomicInteger(0),
        Statistic.MetadataEncrypted to AtomicInteger(0),
        Statistic.FileDecrypted to AtomicInteger(0),
        Statistic.MetadataDecrypted to AtomicInteger(0)
    )

    override fun encrypt(source: Source, fileSecret: DeviceFileSecret): Source {
        stats[Statistic.FileEncrypted]?.getAndIncrement()
        return Buffer().write("file-encrypted".toByteArray())
    }

    override fun encrypt(sink: Sink, fileSecret: DeviceFileSecret): Sink {
        stats[Statistic.FileEncrypted]?.getAndIncrement()
        return sink
    }

    override fun encrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source {
        stats[Statistic.MetadataEncrypted]?.getAndIncrement()
        return Buffer().write("metadata-encrypted".toByteArray())
    }

    override val maxPlaintextSize: Long = 16 * 1024

    override fun decrypt(source: Source, fileSecret: DeviceFileSecret): Source {
        stats[Statistic.FileDecrypted]?.getAndIncrement()
        return Buffer().write("file-decrypted".toByteArray())
    }

    override fun decrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source {
        stats[Statistic.MetadataDecrypted]?.getAndIncrement()
        return Buffer().write("metadata-decrypted".toByteArray())
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object FileEncrypted : Statistic()
        object MetadataEncrypted : Statistic()
        object FileDecrypted : Statistic()
        object MetadataDecrypted : Statistic()
    }
}

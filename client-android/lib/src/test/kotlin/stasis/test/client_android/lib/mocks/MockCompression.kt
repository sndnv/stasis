package stasis.test.client_android.lib.mocks

import okio.Source
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.compression.Compressor
import stasis.client_android.lib.compression.Decoder
import stasis.client_android.lib.compression.Encoder
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.TargetEntity
import java.util.concurrent.atomic.AtomicInteger

open class MockCompression : Compression, Compressor {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.Compressed to AtomicInteger(0),
        Statistic.Decompressed to AtomicInteger(0)
    )

    override fun defaultCompression(): Compressor = this

    override fun disabledExtensions(): Set<String> = setOf("test")

    override fun name(): String = "mock"

    override fun encoderFor(entity: SourceEntity): Encoder = this

    override fun decoderFor(entity: TargetEntity): Decoder = this

    override fun compress(source: Source): Source {
        stats[Statistic.Compressed]?.getAndIncrement()
        return source
    }

    override fun decompress(source: Source): Source {
        stats[Statistic.Decompressed]?.getAndIncrement()
        return source
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object Compressed : Statistic()
        object Decompressed : Statistic()
    }
}

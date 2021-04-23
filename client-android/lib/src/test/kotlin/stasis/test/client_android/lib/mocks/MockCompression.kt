package stasis.test.client_android.lib.mocks

import okio.Buffer
import okio.Source
import stasis.client_android.lib.compression.Decoder
import stasis.client_android.lib.compression.Encoder
import java.util.concurrent.atomic.AtomicInteger

open class MockCompression() : Encoder, Decoder {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.Compressed to AtomicInteger(0),
        Statistic.Decompressed to AtomicInteger(0)
    )

    override fun compress(source: Source): Source {
        stats[Statistic.Compressed]?.getAndIncrement()
        return Buffer().writeUtf8("compressed")
    }

    override fun decompress(source: Source): Source {
        stats[Statistic.Decompressed]?.getAndIncrement()
        return Buffer().writeUtf8("decompressed")
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object Compressed : Statistic()
        object Decompressed : Statistic()
    }
}

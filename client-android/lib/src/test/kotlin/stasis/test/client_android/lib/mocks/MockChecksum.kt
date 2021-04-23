package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.analysis.Checksum
import java.math.BigInteger
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class MockChecksum(val checksums: Map<Path, BigInteger>) : Checksum {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.ChecksumCalculated to AtomicInteger(0),
    )

    override suspend fun calculate(file: Path): BigInteger {
        stats[Statistic.ChecksumCalculated]?.getAndIncrement()

        when (val checksum = checksums[file]) {
            null -> throw IllegalArgumentException("No checksum found for file [$file]")
            else -> return checksum
        }
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object ChecksumCalculated : Statistic()
    }
}

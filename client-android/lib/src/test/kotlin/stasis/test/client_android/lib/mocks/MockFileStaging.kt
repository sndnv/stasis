package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.staging.FileStaging
import stasis.test.client_android.lib.ResourceHelpers
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

open class MockFileStaging : FileStaging {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.TemporaryCreated to AtomicInteger(0),
        Statistic.TemporaryDiscarded to AtomicInteger(0),
        Statistic.Destaged to AtomicInteger(0)
    )

    private var fs: FileSystem

    init {
        val (mockFs, _) = ResourceHelpers.createMockFileSystem(ResourceHelpers.FileSystemSetup.Unix)
        fs = mockFs
    }

    override suspend fun temporary(): Path {
        stats[Statistic.TemporaryCreated]?.getAndIncrement()
        return fs.getPath("/${UUID.randomUUID()}")
    }

    override suspend fun discard(file: Path) {
        stats[Statistic.TemporaryDiscarded]?.getAndIncrement()
    }

    override suspend fun destage(from: Path, to: Path) {
        stats[Statistic.Destaged]?.getAndIncrement()
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object TemporaryCreated : Statistic()
        object TemporaryDiscarded : Statistic()
        object Destaged : Statistic()
    }
}

package stasis.test.client_android.lib.mocks

import okio.Buffer
import okio.ByteString
import okio.Source
import okio.buffer
import stasis.client_android.lib.api.clients.ServerCoreEndpointClient
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.core.NodeId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

open class MockServerCoreEndpointClient(
    override val self: NodeId,
    private val crates: Map<CrateId, ByteString>,
    private val pushDisabled: Boolean = false,
    private val pullDisabled: Boolean = false
) : ServerCoreEndpointClient {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.CratePushed to AtomicInteger(0),
        Statistic.CratePulled to AtomicInteger(0)
    )

    override val server: String = "mock-core-server"

    override suspend fun push(manifest: Manifest, content: Source) {
        if (!pushDisabled) {
            content.buffer().readByteArray()
            stats[Statistic.CratePushed]?.getAndIncrement()
        } else {
            throw RuntimeException("[pushDisabled] is set to [true]")
        }
    }

    override suspend fun pull(crate: CrateId): Source? {
        if (!pullDisabled) {
            return crates[crate]?.let {
                stats[Statistic.CratePulled]?.getAndIncrement()
                Buffer().write(it)
            }
        } else {
            throw RuntimeException("[pullDisabled] is set to [true]")
        }
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object CratePushed : Statistic()
        object CratePulled : Statistic()
    }

    companion object {
        operator fun invoke(): MockServerCoreEndpointClient = MockServerCoreEndpointClient(
            self = UUID.randomUUID(),
            crates = emptyMap()
        )
    }
}

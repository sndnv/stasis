package stasis.client_android.lib.ops.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.plus
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.stages.EntityCollection
import stasis.client_android.lib.ops.backup.stages.EntityDiscovery
import stasis.client_android.lib.ops.backup.stages.EntityProcessing
import stasis.client_android.lib.ops.backup.stages.MetadataCollection
import stasis.client_android.lib.ops.backup.stages.MetadataPush
import stasis.client_android.lib.ops.exceptions.EntityProcessingFailure
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Success
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class Backup(
    private val descriptor: Descriptor,
    private val providers: Providers
) : Operation {
    override val id: OperationId = descriptor.collector.existingState()?.operation ?: Operation.generateId()

    override fun type(): Operation.Type = Operation.Type.Backup

    override fun start(withScope: CoroutineScope, f: (Throwable?) -> Unit) {
        require(jobRef.get() == null) { "Backup [$id] already started" }

        val supervisor = SupervisorJob()

        val job = (withScope + supervisor).async {
            try {
                when (descriptor.collector) {
                    is Descriptor.Collector.WithState -> Unit // do nothing
                    else -> providers.track.started(operation = id, definition = descriptor.targetDataset.id)
                }

                stages.entityDiscovery(id)
                    .let { flow -> stages.entityCollection(id, flow) }
                    .let { flow -> stages.entityProcessing(id, flow) }
                    .let { flow -> stages.metadataCollection(id, flow, descriptor.collector.existingState()) }
                    .let { flow -> stages.metadataPush(id, flow) }
                    .collect()

                providers.track.completed(id)
            } catch (e: EntityProcessingFailure) {
                providers.track.failureEncountered(id, e.entity, e.cause)
                throw e
            } catch (e: Throwable) {
                providers.track.failureEncountered(id, e)
                throw e
            }
        }

        job.invokeOnCompletion { e ->
            supervisor.cancel()
            f(e)
        }

        jobRef.set(job)
    }

    override fun stop() {
        val job = jobRef.get()
        require(job != null) { "Backup [$id] not started" }

        job.cancel(cause = CancellationException("Cancelled by user"))
    }

    private val jobRef: AtomicReference<Job> = AtomicReference()

    private val stages =
        object : EntityDiscovery, EntityCollection, EntityProcessing, MetadataCollection, MetadataPush {
            override val collector: EntityDiscovery.Collector = descriptor.collector.asDiscoveryCollector()
            override val targetDataset: DatasetDefinition = descriptor.targetDataset
            override val latestEntry: DatasetEntry? = descriptor.latestEntry
            override val latestMetadata: DatasetMetadata? = descriptor.latestMetadata
            override val deviceSecret: DeviceSecret = descriptor.deviceSecret
            override val providers: Providers = this@Backup.providers
            override val maxPartSize: Long = descriptor.limits.maxPartSize
        }

    data class Descriptor(
        val targetDataset: DatasetDefinition,
        val latestEntry: DatasetEntry?,
        val latestMetadata: DatasetMetadata?,
        val deviceSecret: DeviceSecret,
        val collector: Collector,
        val limits: Limits
    ) {
        companion object {
            suspend operator fun invoke(
                definition: DatasetDefinitionId,
                collector: Collector,
                deviceSecret: DeviceSecret,
                limits: Limits,
                providers: Providers
            ): Try<Descriptor> =
                providers.clients.api.datasetDefinition(definition = definition)
                    .flatMap { targetDataset ->
                        providers.clients.api.latestEntry(definition = definition, until = null)
                            .flatMap { latestEntry ->
                                val metadata = latestEntry?.let {
                                    providers.clients.api.datasetMetadata(entry = latestEntry)
                                } ?: Success<DatasetMetadata?>(null)

                                metadata.map { latestMetadata ->
                                    Descriptor(
                                        targetDataset = targetDataset,
                                        latestEntry = latestEntry,
                                        latestMetadata = latestMetadata,
                                        deviceSecret = deviceSecret,
                                        collector = collector,
                                        limits = limits
                                    )
                                }
                            }
                    }
        }

        sealed class Collector {
            abstract fun asDiscoveryCollector(): EntityDiscovery.Collector
            abstract fun existingState(): BackupState?

            data class WithRules(val rules: List<Rule>) : Collector() {
                override fun asDiscoveryCollector(): EntityDiscovery.Collector =
                    EntityDiscovery.Collector.WithRules(rules)

                override fun existingState(): BackupState? = null
            }

            data class WithEntities(val entities: List<Path>) : Collector() {
                override fun asDiscoveryCollector(): EntityDiscovery.Collector =
                    EntityDiscovery.Collector.WithEntities(entities)

                override fun existingState(): BackupState? = null
            }

            data class WithState(val state: BackupState) : Collector() {
                override fun asDiscoveryCollector(): EntityDiscovery.Collector =
                    EntityDiscovery.Collector.WithState(state)

                override fun existingState(): BackupState = state
            }
        }

        data class Limits(
            val maxPartSize: Long
        )
    }
}

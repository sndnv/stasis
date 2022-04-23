package stasis.client_android.lib.ops.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.plus
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.collection.BackupMetadataCollector
import stasis.client_android.lib.collection.DefaultBackupCollector
import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.stages.EntityCollection
import stasis.client_android.lib.ops.backup.stages.EntityProcessing
import stasis.client_android.lib.ops.backup.stages.MetadataCollection
import stasis.client_android.lib.ops.backup.stages.MetadataPush
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
    override val id: OperationId = Operation.generateId()

    override fun type(): Operation.Type = Operation.Type.Backup

    override fun start(withScope: CoroutineScope, f: (Throwable?) -> Unit) {
        require(jobRef.get() == null) { "Backup [$id] already started" }

        val supervisor = SupervisorJob()

        val job = (withScope + supervisor).async {
            try {
                when (val collector = descriptor.collector) {
                    is Descriptor.Collector.WithRules -> providers.track.specificationProcessed(
                        operation = id,
                        unmatched = collector.spec.unmatched
                    )
                    else -> Unit // do nothing
                }

                stages.entityCollection(id)
                    .let { flow -> stages.entityProcessing(id, flow) }
                    .let { flow -> stages.metadataCollection(id, flow) }
                    .let { flow -> stages.metadataPush(id, flow) }
                    .collect()
            } catch (e: Throwable) {
                providers.track.failureEncountered(id, e)
                throw e
            } finally {
                providers.track.completed(id)
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
        object : EntityCollection, EntityProcessing, MetadataCollection, MetadataPush {
            override val targetDataset: DatasetDefinition = descriptor.targetDataset
            override val latestEntry: DatasetEntry? = descriptor.latestEntry
            override val latestMetadata: DatasetMetadata? = descriptor.latestMetadata
            override val deviceSecret: DeviceSecret = descriptor.deviceSecret
            override val providers: Providers = this@Backup.providers
            override val collector: BackupCollector = descriptor.toBackupCollector(providers = this@Backup.providers)
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
        fun toBackupCollector(providers: Providers): BackupCollector =
            when (collector) {
                is Collector.WithRules -> DefaultBackupCollector(
                    entities = collector.spec.included,
                    latestMetadata = latestMetadata,
                    metadataCollector = BackupMetadataCollector.Default(checksum = providers.checksum),
                    api = providers.clients.api
                )

                is Collector.WithEntities -> DefaultBackupCollector(
                    entities = collector.entities,
                    latestMetadata = latestMetadata,
                    metadataCollector = BackupMetadataCollector.Default(checksum = providers.checksum),
                    api = providers.clients.api
                )
            }

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
            data class WithRules(val spec: Specification) : Collector()
            data class WithEntities(val entities: List<Path>) : Collector()
        }

        data class Limits(
            val maxPartSize: Long
        )
    }
}

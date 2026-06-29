package stasis.client_android.lib.ops.recovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.plus
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Recovery.Destination.Companion.toTargetEntityDestination
import stasis.client_android.lib.ops.recovery.stages.EntityCollection
import stasis.client_android.lib.ops.recovery.stages.EntityProcessing
import stasis.client_android.lib.ops.recovery.stages.MetadataApplication
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.nio.file.FileSystem
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class Recovery(
    private val descriptor: Descriptor,
    private val providers: Providers
) : Operation {
    override val id: OperationId = Operation.generateId()

    override fun type(): Operation.Type = Operation.Type.Recovery

    override fun start(withScope: CoroutineScope, f: (Throwable?) -> Unit) {
        require(jobRef.get() == null) { "Recovery [$id] already started" }

        val supervisor = SupervisorJob()

        val job = (withScope + supervisor).async {
            try {
                providers.track.started(operation = id)

                stages.entityCollection(id, descriptor.filesystem)
                    .let { flow -> stages.entityProcessing(id, flow) }
                    .let { flow -> stages.metadataApplication(id, flow) }
                    .collect()

                providers.track.completed(id)
            } catch (e: Throwable) {
                providers.track.failureEncountered(id, e)
                providers.analytics.recordFailure(e)
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
        require(job != null) { "Recovery [$id] not started" }

        job.cancel(cause = CancellationException("Cancelled by user"))
    }

    private val jobRef: AtomicReference<Job> = AtomicReference()

    private val stages = object : EntityCollection, EntityProcessing, MetadataApplication {
        override val deviceSecret: DeviceSecret = descriptor.deviceSecret
        override val providers: Providers = this@Recovery.providers
        override val targetMetadata: DatasetMetadata = descriptor.targetMetadata
        override val keep: (String, FilesystemMetadata.EntityState) -> Boolean =
            { entity, _ ->
                (descriptor.entities?.contains(entity) ?: true) &&
                    RecoverySourceKind.forEntity(entity) in descriptor.sources
            }
        override val destination: TargetEntity.Destination =
            descriptor.destination.toTargetEntityDestination(descriptor.filesystem)
    }

    data class Descriptor(
        val targetMetadata: DatasetMetadata,
        val entities: Set<String>?,
        val sources: Set<RecoverySourceKind>,
        val destination: Destination?,
        val deviceSecret: DeviceSecret,
        val filesystem: FileSystem
    ) {
        sealed class Collector {
            data class WithDefinition(val definition: DatasetDefinitionId, val until: Instant?) :
                Collector()

            data class WithEntry(val entry: DatasetEntryId) : Collector()
        }

        companion object {
            suspend operator fun invoke(
                entities: Set<String>?,
                sources: Set<RecoverySourceKind>,
                destination: Destination?,
                collector: Collector,
                deviceSecret: DeviceSecret,
                providers: Providers,
                filesystem: FileSystem
            ): Try<Descriptor> {
                val entry = when (collector) {
                    is Collector.WithDefinition -> providers.clients.api.latestEntry(
                        definition = collector.definition,
                        until = collector.until
                    ).flatMap { entry ->
                        entry
                            ?.let { Success(it) }
                            ?: Failure(
                                IllegalStateException(
                                    "Expected dataset entry for definition [${collector.definition}] but none was found"
                                )
                            )
                    }

                    is Collector.WithEntry -> providers.clients.api.datasetEntry(collector.entry)
                }

                return entry
                    .flatMap { providers.clients.api.datasetMetadata(it) }
                    .map { metadata ->
                        Descriptor(
                            targetMetadata = metadata,
                            entities = entities,
                            sources = sources,
                            destination = destination,
                            deviceSecret = deviceSecret,
                            filesystem = filesystem
                        )
                    }
            }
        }
    }

    data class Destination(
        val path: String,
        val keepStructure: Boolean,
        val preserveExisting: Boolean,
    ) {
        companion object {
            fun Destination?.toTargetEntityDestination(filesystem: FileSystem): TargetEntity.Destination =
                when (this) {
                    null -> TargetEntity.Destination.Default
                    else -> TargetEntity.Destination.Directory(
                        path = filesystem.getPath(path),
                        keepDefaultStructure = keepStructure,
                        preserveExisting = preserveExisting
                    )
                }
        }
    }
}

package stasis.client_android.lib.ops.recovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.plus
import stasis.client_android.lib.collection.DefaultRecoveryCollector
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.collection.RecoveryMetadataCollector
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.exceptions.EntityProcessingFailure
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
import java.nio.file.FileSystems
import java.nio.file.Path
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
                stages.entityCollection(id)
                    .let { flow -> stages.entityProcessing(id, flow) }
                    .let { flow -> stages.metadataApplication(id, flow) }
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
        require(job != null) { "Recovery [$id] not started" }

        job.cancel(cause = CancellationException("Cancelled by user"))
    }

    private val jobRef: AtomicReference<Job> = AtomicReference()

    private val stages = object : EntityCollection, EntityProcessing, MetadataApplication {
        override val deviceSecret: DeviceSecret = descriptor.deviceSecret
        override val providers: Providers = this@Recovery.providers
        override val collector: RecoveryCollector =
            descriptor.toRecoveryCollector(this@Recovery.providers)
    }

    data class Descriptor(
        val targetMetadata: DatasetMetadata,
        val query: PathQuery?,
        val destination: Destination?,
        val deviceSecret: DeviceSecret
    ) {
        fun toRecoveryCollector(providers: Providers): RecoveryCollector =
            DefaultRecoveryCollector(
                targetMetadata = targetMetadata,
                keep = { entity, _ -> query?.matches(entity.toAbsolutePath()) ?: true },
                destination = destination.toTargetEntityDestination(),
                metadataCollector = RecoveryMetadataCollector.Default(checksum = providers.checksum),
                api = providers.clients.api
            )

        sealed class Collector {
            data class WithDefinition(val definition: DatasetDefinitionId, val until: Instant?) :
                Collector()

            data class WithEntry(val entry: DatasetEntryId) : Collector()
        }

        companion object {
            suspend operator fun invoke(
                query: PathQuery?,
                destination: Destination?,
                collector: Collector,
                deviceSecret: DeviceSecret,
                providers: Providers
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
                            query = query,
                            destination = destination,
                            deviceSecret = deviceSecret
                        )
                    }
            }
        }
    }

    sealed class PathQuery {
        abstract fun matches(path: Path): Boolean

        companion object {
            operator fun invoke(query: String): PathQuery =
                if (query.contains("/")) {
                    ForAbsolutePath(query = Regex(query))
                } else {
                    ForFileName(query = Regex(query))
                }
        }

        data class ForAbsolutePath(val query: Regex) : PathQuery() {
            override fun matches(path: Path): Boolean =
                query.toPattern().matcher(path.toAbsolutePath().toString()).find()
        }

        data class ForFileName(val query: Regex) : PathQuery() {
            override fun matches(path: Path): Boolean =
                query.toPattern().matcher(path.fileName.toString()).find()
        }
    }

    data class Destination(
        val path: String,
        val keepStructure: Boolean,
        val filesystem: FileSystem
    ) {
        companion object {
            operator fun invoke(path: String, keepStructure: Boolean): Destination =
                Destination(
                    path = path,
                    keepStructure = keepStructure,
                    filesystem = FileSystems.getDefault()
                )

            fun Destination?.toTargetEntityDestination(): TargetEntity.Destination =
                when (this) {
                    null -> TargetEntity.Destination.Default
                    else -> TargetEntity.Destination.Directory(
                        path = filesystem.getPath(path),
                        keepDefaultStructure = keepStructure
                    )
                }
        }
    }
}

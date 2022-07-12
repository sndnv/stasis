package stasis.client_android.tracking

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.BackupTracker
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Either
import java.nio.file.Path

class DefaultBackupTracker(looper: Looper) : BackupTracker, BackupTrackerView {
    private val trackedState: MutableLiveData<Map<OperationId, BackupState>> =
        MutableLiveData(emptyMap())

    private val handler: TrackingHandler = TrackingHandler(looper)

    override val state: LiveData<Map<OperationId, BackupState>> = trackedState

    override fun updates(operation: OperationId): LiveData<BackupState> =
        MediatorLiveData<BackupState>().apply {
            addSource(trackedState) { operations ->
                operations[operation]?.let { state ->
                    postValue(state)
                }
            }
        }

    override fun entityDiscovered(operation: OperationId, entity: Path) = send(
        event = BackupEvent.EntityDiscovered(
            operation = operation,
            entity = entity
        )
    )


    override fun specificationProcessed(
        operation: OperationId,
        unmatched: List<Pair<Rule, Throwable>>
    ) {
        if (unmatched.isNotEmpty()) {
            send(
                event = BackupEvent.SpecificationProcessed(
                    operation = operation,
                    unmatched = unmatched.map { (rule, e) ->
                        "Rule [${rule.asString()}] failed with [${e.message}]"
                    }
                )
            )
        }
    }

    override fun entityExamined(operation: OperationId, entity: Path) = send(
        event = BackupEvent.EntityExamined(
            operation = operation,
            entity = entity
        )
    )

    override fun entityCollected(operation: OperationId, entity: SourceEntity) = send(
        event = BackupEvent.EntityCollected(
            operation = operation,
            entity = entity
        )
    )

    override fun entityProcessingStarted(operation: OperationId, entity: Path, expectedParts: Int) = send(
        event = BackupEvent.EntityProcessingStarted(
            operation = operation,
            entity = entity,
            expectedParts = expectedParts
        )
    )

    override fun entityPartProcessed(operation: OperationId, entity: Path) = send(
        event = BackupEvent.EntityPartProcessed(
            operation = operation,
            entity = entity
        )
    )

    override fun entityProcessed(
        operation: OperationId,
        entity: Path,
        metadata: Either<EntityMetadata, EntityMetadata>
    ) = send(
        event = BackupEvent.EntityProcessed(
            operation = operation,
            entity = entity,
            metadata = metadata
        )
    )


    override fun metadataCollected(operation: OperationId) = send(
        event = BackupEvent.MetadataCollected(
            operation = operation
        )
    )

    override fun metadataPushed(operation: OperationId, entry: DatasetEntryId) = send(
        event = BackupEvent.MetadataPushed(
            operation = operation
        )
    )

    override fun failureEncountered(operation: OperationId, entity: Path, failure: Throwable) = send(
        event = BackupEvent.EntityFailed(
            operation = operation,
            entity = entity,
            reason = failure
        )
    )

    override fun failureEncountered(operation: OperationId, failure: Throwable) = send(
        event = BackupEvent.FailureEncountered(
            operation = operation,
            reason = failure
        )
    )

    override fun completed(operation: OperationId) = send(
        event = BackupEvent.Completed(
            operation = operation
        )
    )

    private fun send(event: BackupEvent) {
        handler.obtainMessage().let { msg ->
            msg.obj = event
            handler.sendMessage(msg)
        }
    }

    private inner class TrackingHandler(looper: Looper) : Handler(looper) {
        private var _state: Map<OperationId, BackupState> = emptyMap()

        override fun handleMessage(msg: Message) {
            Log.v(TAG, "Received backup tracking event [${msg.obj}]")

            when (val event = msg.obj) {
                is BackupEvent -> {
                    val existing = _state.getOrElse(event.operation) { BackupState.start(event.operation) }

                    val updated = when (event) {
                        is BackupEvent.EntityDiscovered -> existing.entityDiscovered(event.entity)
                        is BackupEvent.SpecificationProcessed -> existing.specificationProcessed(event.unmatched)
                        is BackupEvent.EntityExamined -> existing.entityExamined(event.entity)
                        is BackupEvent.EntityCollected -> existing.entityCollected(event.entity)
                        is BackupEvent.EntityProcessingStarted -> existing.entityProcessingStarted(
                            event.entity,
                            event.expectedParts
                        )
                        is BackupEvent.EntityPartProcessed -> existing.entityPartProcessed(event.entity)
                        is BackupEvent.EntityProcessed -> existing.entityProcessed(event.entity, event.metadata)
                        is BackupEvent.EntityFailed -> existing.entityFailed(event.entity, event.reason)
                        is BackupEvent.FailureEncountered -> existing.failureEncountered(event.reason)
                        is BackupEvent.MetadataCollected -> existing.backupMetadataCollected()
                        is BackupEvent.MetadataPushed -> existing.backupMetadataPushed()
                        is BackupEvent.Completed -> existing.backupCompleted()
                    }

                    _state = _state + (event.operation to updated)
                }

                else -> throw IllegalArgumentException("Unexpected message encountered: [$event]")
            }

            trackedState.postValue(_state)
        }
    }

    private sealed class BackupEvent {
        abstract val operation: OperationId

        data class EntityDiscovered(
            override val operation: OperationId,
            val entity: Path
        ) : BackupEvent()

        data class SpecificationProcessed(
            override val operation: OperationId,
            val unmatched: List<String>
        ) : BackupEvent()

        data class EntityExamined(
            override val operation: OperationId,
            val entity: Path
        ) : BackupEvent()

        data class EntityCollected(
            override val operation: OperationId,
            val entity: SourceEntity
        ) : BackupEvent()

        data class EntityProcessingStarted(
            override val operation: OperationId,
            val entity: Path,
            val expectedParts: Int
        ) : BackupEvent()

        data class EntityPartProcessed(
            override val operation: OperationId,
            val entity: Path
        ) : BackupEvent()

        data class EntityProcessed(
            override val operation: OperationId,
            val entity: Path,
            val metadata: Either<EntityMetadata, EntityMetadata>
        ) : BackupEvent()

        data class EntityFailed(
            override val operation: OperationId,
            val entity: Path,
            val reason: Throwable
        ) : BackupEvent()

        data class FailureEncountered(
            override val operation: OperationId,
            val reason: Throwable
        ) : BackupEvent()

        data class MetadataCollected(
            override val operation: OperationId
        ) : BackupEvent()

        data class MetadataPushed(
            override val operation: OperationId
        ) : BackupEvent()

        data class Completed(
            override val operation: OperationId
        ) : BackupEvent()
    }

    companion object {
        private const val TAG: String = "DefaultBackupTracker"
    }
}

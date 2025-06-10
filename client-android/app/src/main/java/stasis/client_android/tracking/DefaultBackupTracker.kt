package stasis.client_android.tracking

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.runBlocking
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.persistence.state.StateStore
import stasis.client_android.lib.tracking.BackupTracker
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.tracking.state.serdes.BackupStateSerdes
import stasis.client_android.lib.utils.Either
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class DefaultBackupTracker(
    context: Context,
    looper: Looper
) : BackupTracker, BackupTrackerView, BackupTrackerManage {
    private val trackedState: MutableLiveData<Map<OperationId, BackupState>> =
        MutableLiveData(emptyMap())

    private val handler: TrackingHandler = TrackingHandler(context, looper)

    override val state: LiveData<Map<OperationId, BackupState>> = trackedState

    override fun updates(operation: OperationId): LiveData<BackupState> =
        MediatorLiveData<BackupState>().apply {
            addSource(trackedState) { operations ->
                operations[operation]?.let { state ->
                    postValue(state)
                }
            }
        }

    override suspend fun stateOf(operation: OperationId): BackupState? {
        return trackedState.value?.get(operation)
    }

    override fun remove(operation: OperationId) {
        handler.obtainMessage().let { msg ->
            msg.obj = TrackerEvent.RemoveOperation(operation)
            handler.sendMessage(msg)
        }
    }

    override fun clear() {
        handler.obtainMessage().let { msg ->
            msg.obj = TrackerEvent.ClearOperations
            handler.sendMessage(msg)
        }
    }

    override fun started(operation: OperationId, definition: DatasetDefinitionId) = send(
        event = BackupEvent.Started(
            operation = operation,
            definition = definition
        )
    )

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

    override fun entitySkipped(operation: OperationId, entity: Path) = send(
        event = BackupEvent.EntitySkipped(
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

    private inner class TrackingHandler(context: Context, looper: Looper) : Handler(looper) {
        private var _state: Map<OperationId, BackupState> = emptyMap()
        private var updates: Int = 0
        private var persistScheduled: Boolean = false

        private val store: StateStore<Map<OperationId, BackupState>> = StateStore(
            target = File(context.filesDir, "state/backups").toPath(),
            serdes = BackupStateSerdes
        )

        init {
            obtainMessage().let { msg ->
                msg.obj = TrackerEvent.RestoreState
                sendMessage(msg)
            }
        }

        override fun handleMessage(msg: Message) {
            when (val event = msg.obj) {
                is BackupEvent -> {
                    val existing = if (event is BackupEvent.Started) {
                        BackupState.start(event.operation, definition = event.definition)
                    } else {
                        val existing = _state[event.operation]

                        require(existing != null) {
                            "Expected existing state for operation [${event.operation}] but none was found"
                        }

                        existing
                    }

                    val updated = when (event) {
                        is BackupEvent.EntityDiscovered -> existing.entityDiscovered(event.entity)
                        is BackupEvent.SpecificationProcessed -> existing.specificationProcessed(event.unmatched)
                        is BackupEvent.EntityExamined -> existing.entityExamined(event.entity)
                        is BackupEvent.EntitySkipped -> existing.entitySkipped(event.entity)
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
                        is BackupEvent.Completed -> {
                            obtainMessage().let { message ->
                                message.obj = TrackerEvent.PersistState
                                sendMessage(message)
                            }
                            existing.backupCompleted()
                        }
                        else -> existing
                    }

                    updates += 1

                    if (updates >= PersistAfterEvents) {
                        obtainMessage().let { message ->
                            message.obj = TrackerEvent.PersistState
                            sendMessageAtFrontOfQueue(message)
                        }
                    }

                    if (!persistScheduled) {
                        persistScheduled = true
                        obtainMessage().let { message ->
                            message.obj = TrackerEvent.PersistState
                            sendMessageDelayed(message, PersistAfterPeriod.toMillis())
                        }
                    }

                    val now = Instant.now()
                    val filtered = _state.filterValues { it.started.plusMillis(MaxRetention.toMillis()).isAfter(now) }

                    _state = filtered + (event.operation to updated)
                    trackedState.postValue(_state)
                }

                is TrackerEvent -> {
                    when (event) {
                        is TrackerEvent.RestoreState -> runBlocking {
                            when (val state = store.restore()) {
                                null -> Unit // do nothing
                                else -> {
                                    _state = state
                                    trackedState.postValue(_state)
                                }
                            }
                        }

                        is TrackerEvent.PersistState -> runBlocking {
                            updates = 0
                            persistScheduled = false
                            store.persist(state = _state)
                        }

                        is TrackerEvent.RemoveOperation -> {
                            _state = _state - event.operation
                            trackedState.postValue(_state)

                            obtainMessage().let { message ->
                                message.obj = TrackerEvent.PersistState
                                sendMessage(message)
                            }
                        }

                        is TrackerEvent.ClearOperations -> {
                            _state = emptyMap()
                            trackedState.postValue(_state)

                            obtainMessage().let { message ->
                                message.obj = TrackerEvent.PersistState
                                sendMessage(message)
                            }
                        }
                    }
                }

                else -> throw IllegalArgumentException("Unexpected message encountered: [$event]")
            }
        }
    }

    private sealed class TrackerEvent {
        data object RestoreState : TrackerEvent()
        data object PersistState : TrackerEvent()
        data class RemoveOperation(val operation: OperationId) : TrackerEvent()
        data object ClearOperations : TrackerEvent()
    }

    private sealed class BackupEvent {
        abstract val operation: OperationId

        data class Started(
            override val operation: OperationId,
            val definition: DatasetDefinitionId
        ) : BackupEvent()

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

        data class EntitySkipped(
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
        private val MaxRetention: Duration = Duration.ofDays(30)
        private const val PersistAfterEvents: Int = 1000
        private val PersistAfterPeriod: Duration = Duration.ofSeconds(30)
    }
}

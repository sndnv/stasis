package stasis.client_android.tracking

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.runBlocking
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.persistence.state.StateStore
import stasis.client_android.lib.tracking.RecoveryTracker
import stasis.client_android.lib.tracking.state.RecoveryState
import stasis.client_android.lib.tracking.state.serdes.RecoveryStateSerdes
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class DefaultRecoveryTracker(
    context: Context,
    looper: Looper
) : RecoveryTracker, RecoveryTrackerView, RecoveryTrackerManage {
    private val trackedState: MutableLiveData<Map<OperationId, RecoveryState>> =
        MutableLiveData(emptyMap())

    private var handler: TrackingHandler = TrackingHandler(context, looper)

    override val state: LiveData<Map<OperationId, RecoveryState>> = trackedState

    override fun updates(operation: OperationId): LiveData<RecoveryState> =
        MediatorLiveData<RecoveryState>().apply {
            addSource(trackedState) { operations ->
                operations[operation]?.let { state ->
                    postValue(state)
                }
            }
        }

    override suspend fun stateOf(operation: OperationId): RecoveryState? =
        trackedState.value?.get(operation)

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

    override fun started(operation: OperationId) = send(
        event = RecoveryEvent.Started(
            operation = operation
        )
    )

    override fun entityExamined(
        operation: OperationId,
        entity: Path,
        metadataChanged: Boolean,
        contentChanged: Boolean
    ) = send(
        event = RecoveryEvent.EntityExamined(
            operation = operation,
            entity = entity
        )
    )

    override fun entityCollected(operation: OperationId, entity: TargetEntity) = send(
        event = RecoveryEvent.EntityCollected(
            operation = operation,
            entity = entity
        )
    )

    override fun entityProcessingStarted(operation: OperationId, entity: Path, expectedParts: Int) = send(
        event = RecoveryEvent.EntityProcessingStarted(
            operation = operation,
            entity = entity,
            expectedParts = expectedParts
        )
    )

    override fun entityPartProcessed(operation: OperationId, entity: Path) = send(
        event = RecoveryEvent.EntityPartProcessed(
            operation = operation,
            entity = entity
        )
    )

    override fun entityProcessed(operation: OperationId, entity: Path) = send(
        event = RecoveryEvent.EntityProcessed(
            operation = operation,
            entity = entity
        )
    )


    override fun metadataApplied(operation: OperationId, entity: Path) = send(
        event = RecoveryEvent.EntityMetadataApplied(
            operation = operation,
            entity = entity
        )
    )

    override fun failureEncountered(operation: OperationId, entity: Path, failure: Throwable) = send(
        event = RecoveryEvent.EntityFailed(
            operation = operation,
            entity = entity,
            reason = failure
        )
    )

    override fun failureEncountered(operation: OperationId, failure: Throwable) = send(
        event = RecoveryEvent.FailureEncountered(
            operation = operation,
            reason = failure
        )
    )

    override fun completed(operation: OperationId) = send(
        event = RecoveryEvent.Completed(
            operation = operation
        )
    )

    private fun send(event: RecoveryEvent) {
        handler.obtainMessage().let { msg ->
            msg.obj = event
            handler.sendMessage(msg)
        }
    }

    private inner class TrackingHandler(private val context: Context, looper: Looper) :
        Handler(looper) {
        private var _state: Map<OperationId, RecoveryState> = emptyMap()
        private var updates: Int = 0
        private var persistScheduled: Boolean = false

        private val store: StateStore<Map<OperationId, RecoveryState>> = StateStore(
            target = File(context.filesDir, "state/recoveries").toPath(),
            serdes = RecoveryStateSerdes
        )

        private fun scanMediaFiles(paths: Set<Path>) =
            MediaScannerConnection.scanFile(
                context,
                paths.map { it.toAbsolutePath().toString() }.toTypedArray(),
                null,
                null
            )

        init {
            obtainMessage().let { msg ->
                msg.obj = TrackerEvent.RestoreState
                sendMessage(msg)
            }
        }

        override fun handleMessage(msg: Message) {
            when (val event = msg.obj) {
                is RecoveryEvent -> {
                    val existing = if (event is RecoveryEvent.Started) {
                        RecoveryState.start(event.operation)
                    } else {
                        val existing = _state[event.operation]

                        require(existing != null) {
                            "Expected existing state for operation [${event.operation}] but none was found"
                        }

                        existing
                    }

                    val updated = when (event) {
                        is RecoveryEvent.EntityExamined -> existing.entityExamined(
                            entity = event.entity
                        )

                        is RecoveryEvent.EntityCollected -> existing.entityCollected(
                            entity = event.entity
                        )

                        is RecoveryEvent.EntityProcessingStarted -> existing.entityProcessingStarted(
                            entity = event.entity,
                            expectedParts = event.expectedParts
                        )

                        is RecoveryEvent.EntityPartProcessed -> existing.entityPartProcessed(
                            entity = event.entity
                        )

                        is RecoveryEvent.EntityProcessed -> existing.entityProcessed(
                            entity = event.entity
                        )

                        is RecoveryEvent.EntityMetadataApplied -> existing.entityMetadataApplied(
                            entity = event.entity
                        )

                        is RecoveryEvent.EntityFailed -> existing.entityFailed(
                            entity = event.entity,
                            reason = event.reason
                        )

                        is RecoveryEvent.FailureEncountered -> {
                            scanMediaFiles(paths = existing.entities.metadataApplied)
                            existing.failureEncountered(failure = event.reason)
                        }

                        is RecoveryEvent.Completed -> {
                            obtainMessage().let { message ->
                                message.obj = TrackerEvent.PersistState
                                sendMessage(message)
                            }
                            scanMediaFiles(existing.entities.metadataApplied)
                            existing.recoveryCompleted()
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
                    val filtered = _state.filterValues {
                        it.started.plusMillis(MaxRetention.toMillis()).isAfter(now)
                    }

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
        object RestoreState : TrackerEvent()
        object PersistState : TrackerEvent()
        data class RemoveOperation(val operation: OperationId) : TrackerEvent()
        object ClearOperations : TrackerEvent()
    }

    private sealed class RecoveryEvent {
        abstract val operation: OperationId

        data class Started(
            override val operation: OperationId
        ) : RecoveryEvent()

        data class EntityExamined(
            override val operation: OperationId,
            val entity: Path
        ) : RecoveryEvent()

        data class EntityCollected(
            override val operation: OperationId,
            val entity: TargetEntity
        ) : RecoveryEvent()

        data class EntityProcessingStarted(
            override val operation: OperationId,
            val entity: Path,
            val expectedParts: Int
        ) : RecoveryEvent()

        data class EntityPartProcessed(
            override val operation: OperationId,
            val entity: Path
        ) : RecoveryEvent()

        data class EntityProcessed(
            override val operation: OperationId,
            val entity: Path
        ) : RecoveryEvent()

        data class EntityMetadataApplied(
            override val operation: OperationId,
            val entity: Path
        ) : RecoveryEvent()

        data class EntityFailed(
            override val operation: OperationId,
            val entity: Path,
            val reason: Throwable
        ) : RecoveryEvent()

        data class FailureEncountered(
            override val operation: OperationId,
            val reason: Throwable
        ) : RecoveryEvent()

        data class Completed(
            override val operation: OperationId
        ) : RecoveryEvent()
    }

    companion object {
        private val MaxRetention: Duration = Duration.ofDays(30)
        private const val PersistAfterEvents: Int = 1000
        private val PersistAfterPeriod: Duration = Duration.ofSeconds(30)
    }
}

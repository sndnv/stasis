package stasis.client_android.tracking

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.RecoveryTracker
import stasis.client_android.lib.tracking.state.RecoveryState
import java.nio.file.Path

class DefaultRecoveryTracker(
    looper: Looper
) : RecoveryTracker, RecoveryTrackerView {
    private val trackedState: MutableLiveData<Map<OperationId, RecoveryState>> =
        MutableLiveData(emptyMap())

    private var handler: TrackingHandler = TrackingHandler(looper)

    override val state: LiveData<Map<OperationId, RecoveryState>> = trackedState

    override fun updates(operation: OperationId): LiveData<RecoveryState> =
        MediatorLiveData<RecoveryState>().apply {
            addSource(trackedState) { operations ->
                operations[operation]?.let { state ->
                    postValue(state)
                }
            }
        }

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

    private inner class TrackingHandler(looper: Looper) : Handler(looper) {
        private var _state: Map<OperationId, RecoveryState> = emptyMap()

        override fun handleMessage(msg: Message) {
            Log.v(TAG, "Received recovery tracking event [${msg.obj}]")

            when (val event = msg.obj) {
                is RecoveryEvent -> {
                    val existing = _state.getOrElse(event.operation) { RecoveryState.start(event.operation) }

                    val updated = when (event) {
                        is RecoveryEvent.EntityExamined -> existing.entityExamined(event.entity)
                        is RecoveryEvent.EntityCollected -> existing.entityCollected(event.entity)
                        is RecoveryEvent.EntityProcessingStarted -> existing.entityProcessingStarted(
                            event.entity,
                            event.expectedParts
                        )
                        is RecoveryEvent.EntityPartProcessed -> existing.entityPartProcessed(event.entity)
                        is RecoveryEvent.EntityProcessed -> existing.entityProcessed(event.entity)
                        is RecoveryEvent.EntityMetadataApplied -> existing.entityMetadataApplied(event.entity)
                        is RecoveryEvent.EntityFailed -> existing.entityFailed(event.entity, event.reason)
                        is RecoveryEvent.FailureEncountered -> existing.failureEncountered(event.reason)
                        is RecoveryEvent.Completed -> existing.recoveryCompleted()
                    }

                    _state = _state + (event.operation to updated)
                }

                else -> throw IllegalArgumentException("Unexpected message encountered: [$event]")
            }

            trackedState.postValue(_state)
        }
    }

    private sealed class RecoveryEvent {
        abstract val operation: OperationId

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
        private const val TAG: String = "DefaultRecoveryTracker"
    }
}

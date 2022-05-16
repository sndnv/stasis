package stasis.client_android.tracking

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.exceptions.RuleMatchingFailure
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.BackupTracker
import stasis.client_android.lib.tracking.RecoveryTracker
import stasis.client_android.lib.tracking.ServerTracker
import java.nio.file.Path

class DefaultTracker : TrackerView {
    private val trackedState: MutableLiveData<TrackerView.State> =
        MutableLiveData(TrackerView.State.empty())
    private var handler: TrackingHandler

    init {
        HandlerThread("DefaultTracker", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            handler = TrackingHandler(looper)
        }
    }

    override val state: LiveData<TrackerView.State> = trackedState

    override fun operationUpdates(operation: OperationId): LiveData<Operation.Progress> =
        MediatorLiveData<Operation.Progress>().apply {
            addSource(trackedState) { state ->
                state.operations[operation]?.let { progress ->
                    postValue(progress)
                }
            }
        }

    val backup: BackupTracker = object : BackupTracker {
        override fun entityDiscovered(operation: OperationId, entity: Path) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "discovery",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun specificationProcessed(
            operation: OperationId,
            unmatched: List<Pair<Rule, Throwable>>
        ) {
            if (unmatched.isEmpty()) {
                send(
                    event = TrackingEvent.OperationStepCompleted(
                        operationId = operation,
                        stage = "specification",
                        step = "processing"
                    )
                )
            } else {
                unmatched.map { (rule, e) ->
                    send(
                        event = TrackingEvent.OperationStepFailed(
                            operationId = operation,
                            failure = RuleMatchingFailure(
                                message = "Rule [${rule.asString()}] failed with [${e.message}]"
                            )
                        )
                    )
                }
            }
        }

        override fun entityExamined(
            operation: OperationId,
            entity: Path,
            metadataChanged: Boolean,
            contentChanged: Boolean
        ) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "examination",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun entityCollected(operation: OperationId, entity: Path) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "collection",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun entityProcessed(
            operation: OperationId,
            entity: Path,
            contentChanged: Boolean
        ) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "processing",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun metadataCollected(operation: OperationId) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "metadata",
                    step = "collection"
                )
            )
        }

        override fun metadataPushed(operation: OperationId, entry: DatasetEntryId) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "metadata",
                    step = "push"
                )
            )
        }

        override fun failureEncountered(operation: OperationId, failure: Throwable) {
            send(
                event = TrackingEvent.OperationStepFailed(
                    operationId = operation,
                    failure = failure
                )
            )
        }

        override fun completed(operation: OperationId) {
            send(event = TrackingEvent.OperationCompleted(operationId = operation))
        }
    }

    val recovery: RecoveryTracker = object : RecoveryTracker {
        override fun entityExamined(
            operation: OperationId,
            entity: Path,
            metadataChanged: Boolean,
            contentChanged: Boolean
        ) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "examination",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun entityCollected(operation: OperationId, entity: Path) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "collection",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun entityProcessed(operation: OperationId, entity: Path) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "processing",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun metadataApplied(operation: OperationId, entity: Path) {
            send(
                event = TrackingEvent.OperationStepCompleted(
                    operationId = operation,
                    stage = "metadata-applied",
                    step = entity.toAbsolutePath().toString()
                )
            )
        }

        override fun failureEncountered(operation: OperationId, failure: Throwable) {
            send(
                event = TrackingEvent.OperationStepFailed(
                    operationId = operation,
                    failure = failure
                )
            )
        }

        override fun completed(operation: OperationId) {
            send(event = TrackingEvent.OperationCompleted(operationId = operation))
        }
    }

    val server: ServerTracker = object : ServerTracker {
        override fun reachable(server: String) {
            send(event = TrackingEvent.ServerReachable(server = server))
        }

        override fun unreachable(server: String) {
            send(event = TrackingEvent.ServerUnreachable(server = server))
        }
    }

    private fun send(event: TrackingEvent) {
        handler.obtainMessage().let { msg ->
            msg.obj = event
            handler.sendMessage(msg)
        }
    }

    private inner class TrackingHandler(looper: Looper) : Handler(looper) {
        private var _state: TrackerView.State = TrackerView.State.empty()

        override fun handleMessage(msg: Message) {
            Log.v(TAG, "Received tracking event [${msg.obj}]")

            when (val message = msg.obj) {
                is TrackingEvent.OperationStepCompleted -> _state = _state.withStep(
                    operationId = message.operationId,
                    stage = message.stage,
                    step = message.step
                )

                is TrackingEvent.OperationStepFailed -> _state = _state.withFailure(
                    operationId = message.operationId,
                    failure = message.failure
                )

                is TrackingEvent.OperationCompleted -> _state = _state.completed(
                    operationId = message.operationId
                )

                is TrackingEvent.ServerReachable -> _state = _state.withServer(
                    server = message.server,
                    reachable = true
                )

                is TrackingEvent.ServerUnreachable -> _state = _state.withServer(
                    server = message.server,
                    reachable = false
                )

                else -> throw IllegalArgumentException("Unexpected message encountered: [$message]")
            }

            trackedState.postValue(_state)
        }
    }

    private sealed class TrackingEvent {
        data class OperationStepCompleted(
            val operationId: OperationId,
            val stage: String,
            val step: String
        ) : TrackingEvent()

        data class OperationStepFailed(val operationId: OperationId, val failure: Throwable) :
            TrackingEvent()

        data class OperationCompleted(val operationId: OperationId) : TrackingEvent()
        data class ServerReachable(val server: String) : TrackingEvent()
        data class ServerUnreachable(val server: String) : TrackingEvent()
    }

    companion object {
        private const val TAG: String = "DefaultTracker"
    }
}

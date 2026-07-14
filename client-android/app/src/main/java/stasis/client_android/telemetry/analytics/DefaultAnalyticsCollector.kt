package stasis.client_android.telemetry.analytics

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
import kotlinx.coroutines.runBlocking
import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.telemetry.analytics.AnalyticsPersistence
import stasis.client_android.lib.utils.Try
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class DefaultAnalyticsCollector(
    looper: Looper,
    val app: ApplicationInformation,
    val persistenceInterval: Duration,
    val transmissionInterval: Duration,
    override val persistence: AnalyticsPersistence
) : AnalyticsCollector {
    private val latest: AtomicReference<AnalyticsEntry.Collected> =
        AtomicReference(AnalyticsEntry.collected(app))

    private val handler: CollectorHandler = CollectorHandler(looper)

    override fun recordEvent(name: String, attributes: Map<String, String>) {
        send(CollectorMessage.RecordEvent(name, attributes))
    }

    override fun recordFailure(message: String, stackTrace: String?) {
        send(CollectorMessage.RecordFailure(message, stackTrace))
    }

    override fun state(): Try<AnalyticsEntry> =
        Try.Success(latest.get())

    override fun send() {
        send(CollectorMessage.PersistState(forceTransmit = true))
    }

    private fun send(event: CollectorMessage) {
        handler.obtainMessage().let { msg ->
            msg.obj = event
            handler.sendMessage(msg)
        }
    }

    private inner class CollectorHandler(looper: Looper) : Handler(looper) {
        private var isPersistScheduled: Boolean = false
        private var pending: List<AnalyticsEntry> = emptyList()

        init {
            obtainMessage().let { msg ->
                msg.obj = CollectorMessage.LoadState
                sendMessage(msg)
            }
        }

        override fun handleMessage(msg: Message) {
            when (val message = msg.obj) {
                is CollectorMessage.RecordEvent -> {
                    latest.getAndUpdate { it.withEvent(message.name, message.attributes) }

                    scheduleNextPersist()
                }

                is CollectorMessage.RecordFailure -> {
                    latest.getAndUpdate { it.withFailure(message.message, message.stackTrace) }

                    cancelScheduledPersist()

                    obtainMessage().let { m ->
                        m.obj = CollectorMessage.PersistState(forceTransmit = false)
                        sendMessage(m)
                    }
                }

                is CollectorMessage.PersistState -> {
                    isPersistScheduled = false

                    val entry = latest.get()

                    if (message.forceTransmit || persistence.lastTransmitted.plusMillis(transmissionInterval.toMillis())
                            .isBefore(Instant.now())
                    ) {
                        transmitPending()

                        when (runBlocking { persistence.transmit(entry) }) {
                            is Try.Success -> {
                                val empty = AnalyticsEntry.collected(app)
                                latest.set(empty)
                                persistence.cache(entry = empty)
                            }

                            is Try.Failure -> {
                                persistence.cache(entry = entry)
                            }
                        }
                    } else {
                        persistence.cache(entry = entry)
                    }
                }

                is CollectorMessage.LoadState -> {
                    val restoredPending = when (val result = runBlocking { persistence.restorePending() }) {
                        is Try.Success -> result.value
                        is Try.Failure -> emptyList()
                    }

                    val restored = when (val result = runBlocking { persistence.restore() }) {
                        is Try.Success -> result.value
                        is Try.Failure -> null
                    }

                    when {
                        restored == null -> {
                            pending = restoredPending
                            latest.set(AnalyticsEntry.collected(app))
                        }

                        restored.runtime.app == app.asString() -> {
                            pending = restoredPending
                            latest.set(restored.asCollected())
                        }

                        else -> {
                            val fresh = AnalyticsEntry.collected(app)
                            pending = restoredPending + restored
                            persistence.cache(entry = fresh)
                            persistence.cachePending(pending)
                            latest.set(fresh)
                        }
                    }
                }

                else -> throw IllegalArgumentException("Unexpected message encountered: [$message]")
            }
        }

        private fun transmitPending() {
            if (pending.isEmpty()) return

            val remaining = pending.filterNot { runBlocking { persistence.transmit(it) } is Try.Success }
            if (remaining.size != pending.size) {
                pending = remaining
                persistence.cachePending(pending)
            }
        }

        private fun scheduleNextPersist() {
            if (!isPersistScheduled) {
                postDelayed({
                    obtainMessage().let { m ->
                        m.obj = CollectorMessage.PersistState(forceTransmit = false)
                        sendMessage(m)
                    }
                }, PersistStateTimerKey, persistenceInterval.toMillis())
                isPersistScheduled = true
            }
        }

        private fun cancelScheduledPersist() {
            isPersistScheduled = false
            removeCallbacksAndMessages(PersistStateTimerKey)
        }
    }

    private sealed class CollectorMessage {
        data class PersistState(val forceTransmit: Boolean) : CollectorMessage()
        data object LoadState : CollectorMessage()
        data class RecordEvent(val name: String, val attributes: Map<String, String>) : CollectorMessage()
        data class RecordFailure(val message: String, val stackTrace: String?) : CollectorMessage()
    }

    companion object {
        operator fun invoke(
            app: ApplicationInformation,
            persistenceInterval: Duration,
            transmissionInterval: Duration,
            persistence: AnalyticsPersistence
        ): DefaultAnalyticsCollector {
            val handler = HandlerThread(
                "DefaultAnalyticsCollector",
                Process.THREAD_PRIORITY_BACKGROUND
            ).apply { start() }

            return DefaultAnalyticsCollector(
                looper = handler.looper,
                app = app,
                persistenceInterval = persistenceInterval,
                transmissionInterval = transmissionInterval,
                persistence = persistence
            )
        }

        private object PersistStateTimerKey
    }
}

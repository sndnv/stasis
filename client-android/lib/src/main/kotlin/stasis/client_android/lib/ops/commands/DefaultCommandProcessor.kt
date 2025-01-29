package stasis.client_android.lib.ops.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Companion.recoverWith
import stasis.client_android.lib.utils.Try.Failure
import stasis.core.commands.proto.Command
import java.lang.Long.max
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.cancellation.CancellationException

class DefaultCommandProcessor(
    private val initialDelay: Duration,
    private val interval: Duration,
    private val api: ServerApiEndpointClient,
    private val handlers: CommandProcessor.Handlers,
    private val scope: CoroutineScope
) : CommandProcessor {
    private var job: Job

    init {
        job = scope.launch {
            delay(timeMillis = initialDelay.toMillis())
            scheduleNextCommandRetrieval()
        }
    }

    private suspend fun scheduleNextCommandRetrieval(): Unit = withContext(Dispatchers.IO) {
        try {
            val commands = api.commands(lastSequenceId = handlers.retrieveLastProcessedCommand()).get()
            process(commands)
            delay(timeMillis = fullInterval())
        } catch (_: CancellationException) {
            // do nothing
        } catch (_: Throwable) {
            delay(timeMillis = reducedInterval())
        } finally {
            scheduleNextCommandRetrieval()
        }
    }

    override suspend fun all(): Try<List<Command>> {
        cancel()
        return api.commands(lastSequenceId = null)
            .map { commands ->
                val lastProcessedCommand = handlers.retrieveLastProcessedCommand()
                process(commands.filter { it.sequenceId > lastProcessedCommand })
                reschedule(delay = fullInterval())
                commands
            }.recoverWith {
                reschedule(delay = reducedInterval())
                Failure(it)
            }
    }

    override suspend fun latest(): Try<List<Command>> {
        cancel()
        return api.commands(lastSequenceId = handlers.retrieveLastProcessedCommand())
            .map { commands ->
                process(commands)
                reschedule(delay = fullInterval())
                commands
            }.recoverWith {
                reschedule(delay = reducedInterval())
                Failure(it)
            }
    }

    override suspend fun stop() {
        cancel()
    }

    private fun cancel() {
        job.cancel()
    }

    private fun reschedule(delay: Long) {
        if (!job.isCancelled) {
            job.cancel()
        }

        job = scope.launch {
            delay(timeMillis = delay)
            scheduleNextCommandRetrieval()
        }
    }

    private suspend fun process(commands: List<Command>) {
        if (commands.isNotEmpty()) {
            val lastExecutedCommand = handlers.executeCommands(commands = commands)
            lastExecutedCommand?.let { handlers.persistLastProcessedCommand(it) }
        }
    }

    private fun fullInterval(): Long =
        fuzzyInterval(interval = interval.toMillis())

    private fun reducedInterval(): Long =
        max(
            fuzzyInterval(interval = interval.toMillis() / FailureIntervalReduction),
            initialDelay.toMillis()
        )

    @Suppress("MagicNumber")
    private fun fuzzyInterval(interval: Long): Long {
        val low = (interval - (interval * 0.02)).toLong()
        val high = (interval + (interval * 0.03)).toLong()

        return ThreadLocalRandom.current().nextLong(low, high)
    }

    companion object {
        const val FailureIntervalReduction: Int = 10
    }
}

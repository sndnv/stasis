package stasis.test.client_android.lib.ops.commands

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import stasis.client_android.lib.ops.commands.CommandProcessor
import stasis.client_android.lib.ops.commands.DefaultCommandProcessor
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.core.commands.proto.Command
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class DefaultCommandProcessorSpec : WordSpec({
    val defaultInterval: Duration = Duration.ofMillis(100)

    suspend fun <T> managedProcessor(processor: DefaultCommandProcessor, block: suspend () -> T): T =
        try {
            block()
        } finally {
            processor.stop()
        }

    "A DefaultCommandProcessor" should {
        "retrieve commands periodically" {
            val mockApiClient = MockServerApiEndpointClient()

            val persistLastProcessedCommandCalls = AtomicLong(0)
            val retrieveLastProcessedCommandCalls = AtomicLong(0)
            val executeCommandsCalls = AtomicLong(0)
            val lastProcessedSequenceId = AtomicLong(0)

            val processor = DefaultCommandProcessor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                handlers = object : CommandProcessor.Handlers {
                    override suspend fun persistLastProcessedCommand(sequenceId: Long) {
                        persistLastProcessedCommandCalls.incrementAndGet()
                        lastProcessedSequenceId.set(sequenceId)
                    }

                    override suspend fun retrieveLastProcessedCommand(): Long {
                        retrieveLastProcessedCommandCalls.incrementAndGet()
                        return lastProcessedSequenceId.get()
                    }

                    override suspend fun executeCommands(commands: List<Command>): Long {
                        executeCommandsCalls.incrementAndGet()
                        return commands.maxOfOrNull { it.sequenceId } ?: 0
                    }
                },
                scope = testScope
            )

            managedProcessor(processor) {
                delay(defaultInterval.toMillis() / 2)

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (1)
                persistLastProcessedCommandCalls.get() shouldBe (1)
                retrieveLastProcessedCommandCalls.get() shouldBe (1)
                executeCommandsCalls.get() shouldBe (1)
                lastProcessedSequenceId.get() shouldBe (3)

                delay(defaultInterval.toMillis())

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (2)
                persistLastProcessedCommandCalls.get() shouldBe (1) // no new commands since last retrieval
                retrieveLastProcessedCommandCalls.get() shouldBe (2)
                executeCommandsCalls.get() shouldBe (1) // no new commands since last retrieval
                lastProcessedSequenceId.get() shouldBe (3) // no new commands since last retrieval
            }
        }

        "handle command retrieval failures" {
            val mockApiClient = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                override suspend fun commands(lastSequenceId: Long?): Try<List<Command>> =
                    Failure(RuntimeException("test failure"))
            }

            val persistLastProcessedCommandCalls = AtomicLong(0)
            val retrieveLastProcessedCommandCalls = AtomicLong(0)
            val executeCommandsCalls = AtomicLong(0)
            val lastProcessedSequenceId = AtomicLong(0)

            val processor = DefaultCommandProcessor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                handlers = object : CommandProcessor.Handlers {
                    override suspend fun persistLastProcessedCommand(sequenceId: Long) {
                        persistLastProcessedCommandCalls.incrementAndGet()
                        lastProcessedSequenceId.set(sequenceId)
                    }

                    override suspend fun retrieveLastProcessedCommand(): Long {
                        retrieveLastProcessedCommandCalls.incrementAndGet()
                        return lastProcessedSequenceId.get()
                    }

                    override suspend fun executeCommands(commands: List<Command>): Long {
                        executeCommandsCalls.incrementAndGet()
                        return commands.maxOfOrNull { it.sequenceId } ?: 0
                    }
                },
                scope = testScope
            )

            managedProcessor(processor) {
                delay(defaultInterval.toMillis())

                persistLastProcessedCommandCalls.get() shouldBeGreaterThanOrEqual (0)
                retrieveLastProcessedCommandCalls.get() shouldBeGreaterThanOrEqual (3)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)
            }
        }

        "support retrieving all commands" {
            val mockApiClient = MockServerApiEndpointClient()

            val persistLastProcessedCommandCalls = AtomicLong(0)
            val retrieveLastProcessedCommandCalls = AtomicLong(0)
            val executeCommandsCalls = AtomicLong(0)
            val lastProcessedSequenceId = AtomicLong(0)

            val processor = DefaultCommandProcessor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                handlers = object : CommandProcessor.Handlers {
                    override suspend fun persistLastProcessedCommand(sequenceId: Long) {
                        persistLastProcessedCommandCalls.incrementAndGet()
                        lastProcessedSequenceId.set(sequenceId)
                    }

                    override suspend fun retrieveLastProcessedCommand(): Long {
                        retrieveLastProcessedCommandCalls.incrementAndGet()
                        return lastProcessedSequenceId.get()
                    }

                    override suspend fun executeCommands(commands: List<Command>): Long {
                        executeCommandsCalls.incrementAndGet()
                        return commands.maxOfOrNull { it.sequenceId } ?: 0
                    }
                },
                scope = testScope
            )

            managedProcessor(processor) {
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBe (0)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)

                processor.all().get().size shouldBe (3)

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (1)
                persistLastProcessedCommandCalls.get() shouldBe (1)
                retrieveLastProcessedCommandCalls.get() shouldBe (1)
                executeCommandsCalls.get() shouldBe (1)
                lastProcessedSequenceId.get() shouldBe (3)

                delay(defaultInterval.toMillis() + (defaultInterval.toMillis() / 2))

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (2)
                persistLastProcessedCommandCalls.get() shouldBe (1) // no new commands since last retrieval
                retrieveLastProcessedCommandCalls.get() shouldBe (2)
                executeCommandsCalls.get() shouldBe (1) // no new commands since last retrieval
                lastProcessedSequenceId.get() shouldBe (3) // no new commands since last retrieval
            }
        }

        "handle failures when retrieving all commands" {
            val mockApiClient = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                override suspend fun commands(lastSequenceId: Long?): Try<List<Command>> =
                    Failure(RuntimeException("test failure"))
            }

            val persistLastProcessedCommandCalls = AtomicLong(0)
            val retrieveLastProcessedCommandCalls = AtomicLong(0)
            val executeCommandsCalls = AtomicLong(0)
            val lastProcessedSequenceId = AtomicLong(0)

            val processor = DefaultCommandProcessor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                handlers = object : CommandProcessor.Handlers {
                    override suspend fun persistLastProcessedCommand(sequenceId: Long) {
                        persistLastProcessedCommandCalls.incrementAndGet()
                        lastProcessedSequenceId.set(sequenceId)
                    }

                    override suspend fun retrieveLastProcessedCommand(): Long {
                        retrieveLastProcessedCommandCalls.incrementAndGet()
                        return lastProcessedSequenceId.get()
                    }

                    override suspend fun executeCommands(commands: List<Command>): Long {
                        executeCommandsCalls.incrementAndGet()
                        return commands.maxOfOrNull { it.sequenceId } ?: 0
                    }
                },
                scope = testScope
            )

            managedProcessor(processor) {
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBe (0)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)

                processor.all().failed().get().message shouldBe ("test failure")

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBe (0)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)

                delay(defaultInterval.toMillis())

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBeGreaterThanOrEqual (3)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)
            }
        }

        "support retrieving latest commands" {
            val mockApiClient = MockServerApiEndpointClient()

            val persistLastProcessedCommandCalls = AtomicLong(0)
            val retrieveLastProcessedCommandCalls = AtomicLong(0)
            val executeCommandsCalls = AtomicLong(0)
            val lastProcessedSequenceId = AtomicLong(0)

            val processor = DefaultCommandProcessor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                handlers = object : CommandProcessor.Handlers {
                    override suspend fun persistLastProcessedCommand(sequenceId: Long) {
                        persistLastProcessedCommandCalls.incrementAndGet()
                        lastProcessedSequenceId.set(sequenceId)
                    }

                    override suspend fun retrieveLastProcessedCommand(): Long {
                        retrieveLastProcessedCommandCalls.incrementAndGet()
                        return lastProcessedSequenceId.get()
                    }

                    override suspend fun executeCommands(commands: List<Command>): Long {
                        executeCommandsCalls.incrementAndGet()
                        return commands.maxOfOrNull { it.sequenceId } ?: 0
                    }
                },
                scope = testScope
            )

            managedProcessor(processor) {
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBe (0)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)

                processor.latest().get().size shouldBe (3)

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (1)
                persistLastProcessedCommandCalls.get() shouldBe (1)
                retrieveLastProcessedCommandCalls.get() shouldBe (1)
                executeCommandsCalls.get() shouldBe (1)
                lastProcessedSequenceId.get() shouldBe (3)

                processor.latest().get().size shouldBe (0) // no new commands since last retrieval

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (2)
                persistLastProcessedCommandCalls.get() shouldBe (1) // no new commands since last retrieval
                retrieveLastProcessedCommandCalls.get() shouldBe (2)
                executeCommandsCalls.get() shouldBe (1) // no new commands since last retrieval
                lastProcessedSequenceId.get() shouldBe (3) // no new commands since last retrieval

                delay(defaultInterval.toMillis() + (defaultInterval.toMillis() / 2))

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (3)
                persistLastProcessedCommandCalls.get() shouldBe (1) // no new commands since last retrieval
                retrieveLastProcessedCommandCalls.get() shouldBe (3)
                executeCommandsCalls.get() shouldBe (1) // no new commands since last retrieval
                lastProcessedSequenceId.get() shouldBe (3) // no new commands since last retrieval
            }
        }

        "handle failures when retrieving latest commands" {
            val mockApiClient = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                override suspend fun commands(lastSequenceId: Long?): Try<List<Command>> =
                    Failure(RuntimeException("test failure"))
            }

            val persistLastProcessedCommandCalls = AtomicLong(0)
            val retrieveLastProcessedCommandCalls = AtomicLong(0)
            val executeCommandsCalls = AtomicLong(0)
            val lastProcessedSequenceId = AtomicLong(0)

            val processor = DefaultCommandProcessor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                handlers = object : CommandProcessor.Handlers {
                    override suspend fun persistLastProcessedCommand(sequenceId: Long) {
                        persistLastProcessedCommandCalls.incrementAndGet()
                        lastProcessedSequenceId.set(sequenceId)
                    }

                    override suspend fun retrieveLastProcessedCommand(): Long {
                        retrieveLastProcessedCommandCalls.incrementAndGet()
                        return lastProcessedSequenceId.get()
                    }

                    override suspend fun executeCommands(commands: List<Command>): Long {
                        executeCommandsCalls.incrementAndGet()
                        return commands.maxOfOrNull { it.sequenceId } ?: 0
                    }
                },
                scope = testScope
            )

            managedProcessor(processor) {
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBe (0)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)

                processor.latest().failed().get().message shouldBe ("test failure")

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBe (1)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)

                delay(defaultInterval.toMillis())

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                persistLastProcessedCommandCalls.get() shouldBe (0)
                retrieveLastProcessedCommandCalls.get() shouldBeGreaterThanOrEqual (3)
                executeCommandsCalls.get() shouldBe (0)
                lastProcessedSequenceId.get() shouldBe (0)
            }
        }

        "support stopping itself" {
            val mockApiClient = MockServerApiEndpointClient()

            val processor = DefaultCommandProcessor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                handlers = object : CommandProcessor.Handlers {
                    override suspend fun persistLastProcessedCommand(sequenceId: Long) = Unit
                    override suspend fun retrieveLastProcessedCommand(): Long = 0
                    override suspend fun executeCommands(commands: List<Command>): Long = 0
                },
                scope = testScope
            )

            delay(defaultInterval.toMillis())

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands]!! shouldBeGreaterThanOrEqual (1)

            processor.stop()

            delay(defaultInterval.toMillis() * 2)

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands]!! shouldBeGreaterThanOrEqual (2)
        }
    }
})

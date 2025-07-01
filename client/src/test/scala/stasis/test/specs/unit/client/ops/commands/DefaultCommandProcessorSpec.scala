package stasis.test.specs.unit.client.ops.commands

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.slf4j.Logger

import stasis.client.api.clients.Clients
import stasis.client.ops.commands.CommandProcessor
import stasis.client.ops.commands.DefaultCommandProcessor
import stasis.core.commands.proto.Command
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient

class DefaultCommandProcessorSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll with AsyncMockitoSugar {
  "A DefaultCommandProcessor" should "support processing commands" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val successfulExecutions = new AtomicLong(0)
    val failedExecutions = new AtomicLong(0)

    val handlers = new CommandProcessor.Handlers {
      override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
        persistLastProcessedCommandCalls.incrementAndGet()
        Future.successful(Done)
      }

      override def retrieveLastProcessedCommand(): Future[Option[Long]] =
        Future.successful(None)

      override def executeCommands(commands: Seq[Command]): Future[Long] = {
        executeCommandsCalls.incrementAndGet()
        Future.successful(commands.map(_.sequenceId).max)
      }
    }

    val resultHandlers = new DefaultCommandProcessor.CommandProcessingResultHandlers {
      override def onSuccess(commands: Int): Unit = {
        val _ = successfulExecutions.incrementAndGet()
      }

      override def onFailure(commands: Int, e: Throwable): Unit = {
        val _ = failedExecutions.incrementAndGet()
      }
    }

    DefaultCommandProcessor.process(
      commandHandlers = handlers,
      resultHandlers = resultHandlers,
      commands = mockApiClient.commands(lastSequenceId = None).await
    )(typedSystem.executionContext)

    eventually[Assertion] {
      persistLastProcessedCommandCalls.get() should be(1)
      executeCommandsCalls.get() should be(1)
    }

    successfulExecutions.get() should be(1)
    failedExecutions.get() should be(0)
  }

  it should "skip processing commands when none are provided" in withRetry {
    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val successfulExecutions = new AtomicLong(0)
    val failedExecutions = new AtomicLong(0)

    val handlers = new CommandProcessor.Handlers {
      override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
        persistLastProcessedCommandCalls.incrementAndGet()
        Future.successful(Done)
      }

      override def retrieveLastProcessedCommand(): Future[Option[Long]] =
        Future.successful(None)

      override def executeCommands(commands: Seq[Command]): Future[Long] = {
        executeCommandsCalls.incrementAndGet()
        Future.successful(commands.map(_.sequenceId).max)
      }
    }

    val resultHandlers = new DefaultCommandProcessor.CommandProcessingResultHandlers {
      override def onSuccess(commands: Int): Unit = {
        val _ = successfulExecutions.incrementAndGet()
      }

      override def onFailure(commands: Int, e: Throwable): Unit = {
        val _ = failedExecutions.incrementAndGet()
      }
    }

    DefaultCommandProcessor.process(
      commandHandlers = handlers,
      resultHandlers = resultHandlers,
      commands = Seq.empty
    )(typedSystem.executionContext)

    eventually[Assertion] {
      persistLastProcessedCommandCalls.get() should be(0)
      executeCommandsCalls.get() should be(0)
    }

    successfulExecutions.get() should be(0)
    failedExecutions.get() should be(0)
  }

  it should "handle processing failures" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val successfulExecutions = new AtomicLong(0)
    val failedExecutions = new AtomicLong(0)

    val handlers = new CommandProcessor.Handlers {
      override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
        persistLastProcessedCommandCalls.incrementAndGet()
        Future.failed(new RuntimeException("Test failure"))
      }

      override def retrieveLastProcessedCommand(): Future[Option[Long]] =
        Future.successful(None)

      override def executeCommands(commands: Seq[Command]): Future[Long] = {
        executeCommandsCalls.incrementAndGet()
        Future.successful(commands.map(_.sequenceId).max)
      }
    }

    val resultHandlers = new DefaultCommandProcessor.CommandProcessingResultHandlers {
      override def onSuccess(commands: Int): Unit = {
        val _ = successfulExecutions.incrementAndGet()
      }

      override def onFailure(commands: Int, e: Throwable): Unit = {
        val _ = failedExecutions.incrementAndGet()
      }
    }

    DefaultCommandProcessor.process(
      commandHandlers = handlers,
      resultHandlers = resultHandlers,
      commands = mockApiClient.commands(lastSequenceId = None).await
    )(typedSystem.executionContext)

    eventually[Assertion] {
      persistLastProcessedCommandCalls.get() should be(1)
      executeCommandsCalls.get() should be(1)
    }

    successfulExecutions.get() should be(0)
    failedExecutions.get() should be(1)
  }

  it should "support handling successful command results" in {
    val log = mock[Logger]

    val handlers = new DefaultCommandProcessor.CommandProcessingResultHandlers.Default(log)

    handlers.onSuccess(commands = 42)

    verify(log).debug("Successfully processed [{}] command(s)", 42)

    succeed
  }

  it should "support handling failed command results" in {
    val log = mock[Logger]

    val handlers = new DefaultCommandProcessor.CommandProcessingResultHandlers.Default(log)

    handlers.onFailure(commands = 42, e = new RuntimeException("Test failure"))

    verify(log).error("Processing of [{}] command(s) failed: [{} - {}]", 42, "RuntimeException", "Test failure")

    succeed
  }

  it should "support calculating a full collection interval" in withRetry {
    implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

    (0 to 10000).foreach { _ =>
      val generated = DefaultCommandProcessor.fullInterval(interval = 1000.millis)
      generated.toMillis should (be >= 900L and be < 1100L)
    }

    succeed
  }

  it should "support calculating a partial collection interval" in withRetry {
    implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

    (0 to 10000).foreach { _ =>
      val generated = DefaultCommandProcessor
        .reducedInterval(
          interval = 1000.millis,
          initialDelay = 100.millis
        )

      // the reduced interval is 10x smaller than the original but the minimum is 100ms
      generated.toMillis should (be >= 100L and be < 110L)
    }

    succeed
  }

  it should "retrieve commands periodically" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()

    val initialDelay = 50.millis

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val retrieveLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val lastProcessedSequenceId = new AtomicLong(0)

    val processor = DefaultCommandProcessor(
      initialDelay = initialDelay,
      interval = defaultInterval,
      clients = Clients(api = mockApiClient, core = null),
      handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
          persistLastProcessedCommandCalls.incrementAndGet()
          lastProcessedSequenceId.set(sequenceId)
          Future.successful(Done)
        }

        override def retrieveLastProcessedCommand(): Future[Option[Long]] = {
          retrieveLastProcessedCommandCalls.incrementAndGet()
          Future.successful(Some(lastProcessedSequenceId.get()))
        }

        override def executeCommands(commands: Seq[Command]): Future[Long] = {
          executeCommandsCalls.incrementAndGet()
          Future.successful(commands.map(_.sequenceId).max)
        }
      }
    )

    managedProcessor(processor) {
      await(initialDelay / 2)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be(0)
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)

      await(initialDelay)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(1)
      persistLastProcessedCommandCalls.get() should be(1)
      retrieveLastProcessedCommandCalls.get() should be(1)
      executeCommandsCalls.get() should be(1)
      lastProcessedSequenceId.get() should be(3)

      await(defaultInterval)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be >= 2
      persistLastProcessedCommandCalls.get() should be(1) // no new commands since last retrieval
      retrieveLastProcessedCommandCalls.get() should be(2)
      executeCommandsCalls.get() should be(1) // no new commands since last retrieval
      lastProcessedSequenceId.get() should be(3) // no new commands since last retrieval
    }
  }

  it should "handle command retrieval failures" in withRetry {
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def commands(lastSequenceId: Option[Long]): Future[Seq[Command]] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val retrieveLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val lastProcessedSequenceId = new AtomicLong(0)

    val processor = DefaultCommandProcessor(
      initialDelay = 0.millis,
      interval = defaultInterval,
      clients = Clients(api = mockApiClient, core = null),
      handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
          persistLastProcessedCommandCalls.incrementAndGet()
          lastProcessedSequenceId.set(sequenceId)
          Future.successful(Done)
        }

        override def retrieveLastProcessedCommand(): Future[Option[Long]] = {
          retrieveLastProcessedCommandCalls.incrementAndGet()
          Future.successful(Some(lastProcessedSequenceId.get()))
        }

        override def executeCommands(commands: Seq[Command]): Future[Long] = {
          executeCommandsCalls.incrementAndGet()
          Future.successful(commands.map(_.sequenceId).max)
        }
      }
    )

    managedProcessor(processor) {
      await(defaultInterval)

      persistLastProcessedCommandCalls.get() should be >= 0L
      retrieveLastProcessedCommandCalls.get() should be >= 3L
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)
    }
  }

  it should "support retrieving all commands" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()

    val initialDelay = 50.millis

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val retrieveLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val lastProcessedSequenceId = new AtomicLong(0)

    val processor = DefaultCommandProcessor(
      initialDelay = initialDelay,
      interval = defaultInterval,
      clients = Clients(api = mockApiClient, core = null),
      handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
          persistLastProcessedCommandCalls.incrementAndGet()
          lastProcessedSequenceId.set(sequenceId)
          Future.successful(Done)
        }

        override def retrieveLastProcessedCommand(): Future[Option[Long]] = {
          retrieveLastProcessedCommandCalls.incrementAndGet()
          Future.successful(Some(lastProcessedSequenceId.get()))
        }

        override def executeCommands(commands: Seq[Command]): Future[Long] = {
          executeCommandsCalls.incrementAndGet()
          Future.successful(commands.map(_.sequenceId).max)
        }
      }
    )

    managedProcessor(processor) {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be(0)
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)

      processor.all().await.size should be(3)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(1)
      persistLastProcessedCommandCalls.get() should be(1)
      retrieveLastProcessedCommandCalls.get() should be(1)
      executeCommandsCalls.get() should be(1)
      lastProcessedSequenceId.get() should be(3)

      processor.all().await.size should be(3)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(2)
      persistLastProcessedCommandCalls.get() should be(1) // no new commands since last retrieval
      retrieveLastProcessedCommandCalls.get() should be(2)
      executeCommandsCalls.get() should be(1) // no new commands since last retrieval
      lastProcessedSequenceId.get() should be(3) // no new commands since last retrieval

      await(defaultInterval + (defaultInterval / 2))

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(3)
      persistLastProcessedCommandCalls.get() should be(1) // no new commands since last retrieval
      retrieveLastProcessedCommandCalls.get() should be(3)
      executeCommandsCalls.get() should be(1) // no new commands since last retrieval
      lastProcessedSequenceId.get() should be(3) // no new commands since last retrieval
    }
  }

  it should "handle failures when retrieving all commands" in withRetry {
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def commands(lastSequenceId: Option[Long]): Future[Seq[Command]] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val retrieveLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val lastProcessedSequenceId = new AtomicLong(0)

    val processor = DefaultCommandProcessor(
      initialDelay = 50.millis,
      interval = defaultInterval,
      clients = Clients(api = mockApiClient, core = null),
      handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
          persistLastProcessedCommandCalls.incrementAndGet()
          lastProcessedSequenceId.set(sequenceId)
          Future.successful(Done)
        }

        override def retrieveLastProcessedCommand(): Future[Option[Long]] = {
          retrieveLastProcessedCommandCalls.incrementAndGet()
          Future.successful(Some(lastProcessedSequenceId.get()))
        }

        override def executeCommands(commands: Seq[Command]): Future[Long] = {
          executeCommandsCalls.incrementAndGet()
          Future.successful(commands.map(_.sequenceId).max)
        }
      }
    )

    managedProcessor(processor) {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be(0)
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)

      processor.all().failed.await.getMessage should be("Test failure")

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be(1)
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)

      await(defaultInterval)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be >= 3L
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)
    }
  }

  it should "support retrieving latest commands" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()

    val initialDelay = 50.millis

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val retrieveLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val lastProcessedSequenceId = new AtomicLong(0)

    val processor = DefaultCommandProcessor(
      initialDelay = initialDelay,
      interval = defaultInterval,
      clients = Clients(api = mockApiClient, core = null),
      handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
          persistLastProcessedCommandCalls.incrementAndGet()
          lastProcessedSequenceId.set(sequenceId)
          Future.successful(Done)
        }

        override def retrieveLastProcessedCommand(): Future[Option[Long]] = {
          retrieveLastProcessedCommandCalls.incrementAndGet()
          Future.successful(Some(lastProcessedSequenceId.get()))
        }

        override def executeCommands(commands: Seq[Command]): Future[Long] = {
          executeCommandsCalls.incrementAndGet()
          Future.successful(commands.map(_.sequenceId).max)
        }
      }
    )

    managedProcessor(processor) {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be(0)
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)

      processor.latest().await.size should be(3)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(1)
      persistLastProcessedCommandCalls.get() should be(1)
      retrieveLastProcessedCommandCalls.get() should be(1)
      executeCommandsCalls.get() should be(1)
      lastProcessedSequenceId.get() should be(3)

      processor.latest().await.size should be(0) // no new commands since last retrieval

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(2)
      persistLastProcessedCommandCalls.get() should be(1) // no new commands since last retrieval
      retrieveLastProcessedCommandCalls.get() should be(2)
      executeCommandsCalls.get() should be(1) // no new commands since last retrieval
      lastProcessedSequenceId.get() should be(3) // no new commands since last retrieval

      await(defaultInterval + (defaultInterval / 2))

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(3)
      persistLastProcessedCommandCalls.get() should be(1) // no new commands since last retrieval
      retrieveLastProcessedCommandCalls.get() should be(3)
      executeCommandsCalls.get() should be(1) // no new commands since last retrieval
      lastProcessedSequenceId.get() should be(3) // no new commands since last retrieval
    }
  }

  it should "handle failures when retrieving latest commands" in withRetry {
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def commands(lastSequenceId: Option[Long]): Future[Seq[Command]] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val persistLastProcessedCommandCalls = new AtomicLong(0)
    val retrieveLastProcessedCommandCalls = new AtomicLong(0)
    val executeCommandsCalls = new AtomicLong(0)
    val lastProcessedSequenceId = new AtomicLong(0)

    val processor = DefaultCommandProcessor(
      initialDelay = 50.millis,
      interval = defaultInterval,
      clients = Clients(api = mockApiClient, core = null),
      handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = {
          persistLastProcessedCommandCalls.incrementAndGet()
          lastProcessedSequenceId.set(sequenceId)
          Future.successful(Done)
        }

        override def retrieveLastProcessedCommand(): Future[Option[Long]] = {
          retrieveLastProcessedCommandCalls.incrementAndGet()
          Future.successful(Some(lastProcessedSequenceId.get()))
        }

        override def executeCommands(commands: Seq[Command]): Future[Long] = {
          executeCommandsCalls.incrementAndGet()
          Future.successful(commands.map(_.sequenceId).max)
        }
      }
    )

    managedProcessor(processor) {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be(0)
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)

      processor.latest().failed.await.getMessage should be("Test failure")

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be(1)
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)

      await(defaultInterval)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      persistLastProcessedCommandCalls.get() should be(0)
      retrieveLastProcessedCommandCalls.get() should be >= 3L
      executeCommandsCalls.get() should be(0)
      lastProcessedSequenceId.get() should be(0)
    }
  }

  it should "support stopping itself" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()

    val processor = DefaultCommandProcessor(
      initialDelay = 50.millis,
      interval = defaultInterval,
      clients = Clients(api = mockApiClient, core = null),
      handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = Future.successful(Done)
        override def retrieveLastProcessedCommand(): Future[Option[Long]] = Future.successful(None)
        override def executeCommands(commands: Seq[Command]): Future[Long] = Future.successful(0)
      }
    )

    eventually[Assertion] {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(1)
    }

    val _ = processor.stop().await

    await(defaultInterval)

    mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(1)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(7.seconds, 300.milliseconds)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultCommandProcessorSpec"
  )

  private def managedProcessor(processor: DefaultCommandProcessor)(block: => Assertion): Assertion =
    try {
      block
    } finally {
      val _ = processor.stop().await
    }

  private val defaultInterval: FiniteDuration = 200.millis

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}

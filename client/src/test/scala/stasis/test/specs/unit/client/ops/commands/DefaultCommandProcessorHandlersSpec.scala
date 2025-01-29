package stasis.test.specs.unit.client.ops.commands

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.client.ops.commands.DefaultCommandProcessorHandlers
import stasis.client.service.components.{Files => AppFiles}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient

class DefaultCommandProcessorHandlersSpec extends AsyncUnitSpec with ResourceHelpers {
  "A DefaultCommandProcessorHandlers" should "support persisting processed commands" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
      }
    )

    val handlers = DefaultCommandProcessorHandlers(
      executeCommand = _ => Future.successful(Done),
      directory = directory
    )

    val _ = handlers.persistLastProcessedCommand(sequenceId = 42).await

    val expectedContent = """{"last_sequence_id":42}"""

    val actualContent = directory.pullFile(file = AppFiles.CommandState)(executionContext, _.utf8String).await

    actualContent should be(expectedContent)
  }

  it should "support retrieving the last processed command (existing)" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
        Files.writeString(path.resolve(AppFiles.CommandState), """{"last_sequence_id":21}""")
      }
    )

    val handlers = DefaultCommandProcessorHandlers(
      executeCommand = _ => Future.successful(Done),
      directory = directory
    )

    handlers.retrieveLastProcessedCommand().await should be(Some(21))
  }

  it should "support retrieving the last processed command (missing)" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
      }
    )

    val handlers = DefaultCommandProcessorHandlers(
      executeCommand = _ => Future.successful(Done),
      directory = directory
    )

    handlers.retrieveLastProcessedCommand().await should be(None)
  }

  it should "support executing commands" in {
    val mockApiClient = MockServerApiEndpointClient()

    val executedCommands: AtomicInteger = new AtomicInteger(0)

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
      }
    )

    val handlers = DefaultCommandProcessorHandlers(
      executeCommand = _ => {
        val _ = executedCommands.incrementAndGet()
        Future.successful(Done)
      },
      directory = directory
    )

    handlers.executeCommands(commands = mockApiClient.commands(lastSequenceId = None).await).map { lastCommandId =>
      lastCommandId should be(3)
      executedCommands.get() should be(3)
    }
  }

  it should "fail to execute commands if none are provided" in {
    val executedCommands: AtomicInteger = new AtomicInteger(0)

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
      }
    )

    val handlers = DefaultCommandProcessorHandlers(
      executeCommand = _ => {
        val _ = executedCommands.incrementAndGet()
        Future.successful(Done)
      },
      directory = directory
    )

    handlers.executeCommands(commands = Seq.empty).failed.map { e =>
      e.getMessage should be("Unexpected number of commands provided: [0]")
      executedCommands.get() should be(0)
    }
  }

  override implicit def executionContext: ExecutionContext = ExecutionContext.global
}

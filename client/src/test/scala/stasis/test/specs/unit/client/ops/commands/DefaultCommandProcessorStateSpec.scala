package stasis.test.specs.unit.client.ops.commands

import java.nio.file.Files

import scala.concurrent.ExecutionContext

import stasis.client.ops.commands.DefaultCommandProcessorState
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class DefaultCommandProcessorStateSpec extends AsyncUnitSpec with ResourceHelpers {
  "A DefaultCommandProcessorState" should "support persisting state to file" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
      }
    )

    DefaultCommandProcessorState(lastSequenceId = 42)
      .persist(to = stateFile, directory = directory)
      .await

    val expectedContent = """{"last_sequence_id":42}"""

    val actualContent = directory.pullFile(file = stateFile)(executionContext, _.utf8String).await

    actualContent should be(expectedContent)
  }

  it should "support loading state from file" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)

        val file = path.resolve(stateFile)
        Files.createFile(file)
        Files.writeString(file, """{"last_sequence_id":21}""")
      }
    )

    DefaultCommandProcessorState
      .load(from = stateFile, directory = directory)
      .await match {
      case Some(state) => state should be(DefaultCommandProcessorState(lastSequenceId = 21))
      case None        => fail("Expected state but none was found")
    }
  }

  it should "not load state if file is missing" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
      }
    )

    DefaultCommandProcessorState
      .load(from = stateFile, directory = directory)
      .await match {
      case None        => succeed
      case Some(state) => fail(s"Unexpected state received: [$state]")
    }
  }

  private val stateFile: String = "test-file"

  override implicit def executionContext: ExecutionContext = ExecutionContext.global
}

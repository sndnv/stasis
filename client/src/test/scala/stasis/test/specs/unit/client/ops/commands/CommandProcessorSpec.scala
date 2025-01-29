package stasis.test.specs.unit.client.ops.commands

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.client.ops.commands.CommandProcessor
import stasis.core.commands.proto.Command
import stasis.test.specs.unit.AsyncUnitSpec

class CommandProcessorSpec extends AsyncUnitSpec {
  "A CommandProcessor" should "provide the last processed command" in {
    val processor = new CommandProcessor {
      override def all(): Future[Seq[Command]] = Future.successful(Seq.empty)
      override def latest(): Future[Seq[Command]] = Future.successful(Seq.empty)
      override def stop(): Future[Done] = Future.successful(Done)
      override protected val handlers: CommandProcessor.Handlers = new CommandProcessor.Handlers {
        override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = Future.successful(Done)
        override def retrieveLastProcessedCommand(): Future[Option[Long]] = Future.successful(Some(42))
        override def executeCommands(commands: Seq[Command]): Future[Long] = Future.successful(0)
      }
    }

    processor.lastProcessedCommand.map { result =>
      result should be(Some(42))
    }
  }
}

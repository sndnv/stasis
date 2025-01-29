package stasis.test.specs.unit.client.mocks

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.client.ops.commands.CommandProcessor
import stasis.core.commands.proto.Command

class MockCommandProcessor(
  commands: Seq[Command],
  lastProcessed: Option[Long]
) extends CommandProcessor {
  override def all(): Future[Seq[Command]] = Future.successful(commands)

  override def latest(): Future[Seq[Command]] = Future.successful(commands)

  override def stop(): Future[Done] = Future.successful(Done)

  override val handlers: CommandProcessor.Handlers = new CommandProcessor.Handlers {
    override def persistLastProcessedCommand(sequenceId: Long): Future[Done] = Future.successful(Done)
    override def retrieveLastProcessedCommand(): Future[Option[Long]] = Future.successful(lastProcessed)
    override def executeCommands(commands: Seq[Command]): Future[Long] = Future.successful(0)
  }
}

object MockCommandProcessor {
  def apply(): MockCommandProcessor =
    new MockCommandProcessor(commands = Seq.empty, lastProcessed = None)

  def apply(commands: Seq[Command], lastProcessed: Option[Long]): MockCommandProcessor =
    new MockCommandProcessor(commands = commands, lastProcessed = lastProcessed)
}

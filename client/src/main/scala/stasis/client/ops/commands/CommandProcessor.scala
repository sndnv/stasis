package stasis.client.ops.commands

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.core.commands.proto.Command

trait CommandProcessor {
  def all(): Future[Seq[Command]]
  def latest(): Future[Seq[Command]]
  def stop(): Future[Done]

  def lastProcessedCommand: Future[Option[Long]] = handlers.retrieveLastProcessedCommand()

  protected def handlers: CommandProcessor.Handlers
}

object CommandProcessor {
  trait Handlers {
    def persistLastProcessedCommand(sequenceId: Long): Future[Done]
    def retrieveLastProcessedCommand(): Future[Option[Long]]
    def executeCommands(commands: Seq[Command]): Future[Long]
  }
}

package stasis.client.ops.commands

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.client.service.ApplicationDirectory
import stasis.client.service.components.Files
import stasis.core.commands.proto.Command

class DefaultCommandProcessorHandlers(
  executeCommand: Command => Future[Done],
  directory: ApplicationDirectory
)(implicit ec: ExecutionContext)
    extends CommandProcessor.Handlers {
  override def persistLastProcessedCommand(sequenceId: Long): Future[Done] =
    DefaultCommandProcessorState(lastSequenceId = sequenceId)
      .persist(to = Files.CommandState, directory = directory)

  override def retrieveLastProcessedCommand(): Future[Option[Long]] =
    DefaultCommandProcessorState
      .load(from = Files.CommandState, directory = directory)
      .map(_.map(_.lastSequenceId))

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def executeCommands(commands: Seq[Command]): Future[Long] =
    commands
      .sortBy(_.sequenceId)
      .foldLeft(Future.successful(Option.empty[Long])) { case (collected, command) =>
        for {
          _ <- collected
          _ <- executeCommand(command)
        } yield {
          Some(command.sequenceId)
        }
      }
      .map {
        case Some(sequenceId) => sequenceId
        case None => throw new IllegalArgumentException(s"Unexpected number of commands provided: [${commands.size.toString}]")
      }
}

object DefaultCommandProcessorHandlers {
  def apply(
    executeCommand: Command => Future[Done],
    directory: ApplicationDirectory
  )(implicit ec: ExecutionContext): DefaultCommandProcessorHandlers =
    new DefaultCommandProcessorHandlers(executeCommand, directory)
}

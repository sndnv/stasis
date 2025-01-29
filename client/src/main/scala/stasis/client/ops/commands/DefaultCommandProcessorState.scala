package stasis.client.ops.commands

import java.io.FileNotFoundException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.util.ByteString
import play.api.libs.json._

import stasis.client.service.ApplicationDirectory

final case class DefaultCommandProcessorState(lastSequenceId: Long) {
  def persist(to: String, directory: ApplicationDirectory)(implicit ec: ExecutionContext): Future[Done] =
    DefaultCommandProcessorState.persist(state = this, to = to, directory = directory)
}

object DefaultCommandProcessorState {
  def load(
    from: String,
    directory: ApplicationDirectory
  )(implicit ec: ExecutionContext): Future[Option[DefaultCommandProcessorState]] =
    directory
      .pullFile(file = from)(ec, bytes => Json.parse(bytes.toArrayUnsafe()).as[DefaultCommandProcessorState])
      .map(Some.apply)
      .recoverWith { case _: FileNotFoundException => Future.successful(None) }

  def persist(
    state: DefaultCommandProcessorState,
    to: String,
    directory: ApplicationDirectory
  )(implicit ec: ExecutionContext): Future[Done] = {
    val content = Json.toJson(state).toString()

    directory
      .pushFile(file = to, content = ByteString.fromString(content))
      .map { _ => Done }
  }

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val commandStateFormat: Format[DefaultCommandProcessorState] = Json.format[DefaultCommandProcessorState]
}

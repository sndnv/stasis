package stasis.client.ops.exceptions

import scala.util.control.NonFatal

final case class OperationStopped(message: String) extends Exception(message)

object OperationStopped {
  def unapply(t: Throwable): Option[Throwable] =
    Option(t).flatMap {
      case NonFatal(e: OperationStopped) => Some(e)
      case NonFatal(e)                   => unapply(e.getCause)
    }
}

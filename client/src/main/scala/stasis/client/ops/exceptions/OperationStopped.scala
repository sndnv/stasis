package stasis.client.ops.exceptions

final case class OperationStopped(message: String) extends Exception(message)

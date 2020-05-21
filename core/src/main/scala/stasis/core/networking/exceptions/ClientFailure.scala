package stasis.core.networking.exceptions

final case class ClientFailure(override val message: String) extends NetworkingFailure(message)

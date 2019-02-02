package stasis.core.networking.exceptions

final case class EndpointFailure(override val message: String) extends NetworkingFailure(message)

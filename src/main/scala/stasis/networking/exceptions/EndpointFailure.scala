package stasis.networking.exceptions

final case class EndpointFailure(override val message: String) extends NetworkingFailure(message)

package stasis.networking.exceptions

case class EndpointFailure(override val message: String) extends NetworkingFailure(message)

package stasis.routing.exceptions

case class PushFailure(override val message: String) extends RoutingFailure(message)

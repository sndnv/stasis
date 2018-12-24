package stasis.routing.exceptions

case class DiscardFailure(override val message: String) extends RoutingFailure(message)

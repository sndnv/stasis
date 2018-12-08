package stasis.routing.exceptions

case class PullFailure(override val message: String) extends RoutingFailure(message)

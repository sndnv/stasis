package stasis.core.routing.exceptions

final case class PullFailure(override val message: String) extends RoutingFailure(message)

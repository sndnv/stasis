package stasis.core.routing.exceptions

final case class DiscardFailure(override val message: String) extends RoutingFailure(message)

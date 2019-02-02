package stasis.core.routing.exceptions

final case class PushFailure(override val message: String) extends RoutingFailure(message)

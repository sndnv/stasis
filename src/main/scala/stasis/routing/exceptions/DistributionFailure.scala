package stasis.routing.exceptions

final case class DistributionFailure(override val message: String) extends RoutingFailure(message)

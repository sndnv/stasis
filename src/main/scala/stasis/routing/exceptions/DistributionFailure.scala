package stasis.routing.exceptions

case class DistributionFailure(override val message: String) extends RoutingFailure(message)

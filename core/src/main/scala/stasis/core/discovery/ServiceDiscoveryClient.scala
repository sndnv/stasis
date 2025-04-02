package stasis.core.discovery

import scala.concurrent.Future

trait ServiceDiscoveryClient extends ServiceApiClient {
  def attributes: ServiceDiscoveryClient.Attributes
  def latest(isInitialRequest: Boolean): Future[ServiceDiscoveryResult]
}

object ServiceDiscoveryClient {
  trait Attributes { self: Product =>
    def asServiceDiscoveryRequest(isInitialRequest: Boolean): ServiceDiscoveryRequest =
      ServiceDiscoveryRequest(
        isInitialRequest = isInitialRequest,
        attributes = productIterator.zipWithIndex.map { case (value, i) =>
          productElementName(i) -> value.toString
        }.toMap
      )
  }
}

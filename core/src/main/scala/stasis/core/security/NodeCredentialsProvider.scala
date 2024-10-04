package stasis.core.security

import scala.concurrent.Future

import stasis.core.networking.EndpointAddress

trait NodeCredentialsProvider[A <: EndpointAddress, C] {
  def provide(address: A): Future[C]
}

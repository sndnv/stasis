package stasis.core.security

import stasis.core.networking.EndpointAddress

import scala.concurrent.Future

trait NodeCredentialsProvider[A <: EndpointAddress, C] {
  def provide(address: A): Future[C]
}

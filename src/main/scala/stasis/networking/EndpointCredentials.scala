package stasis.networking

trait EndpointCredentials[A <: EndpointAddress, C] {
  def provide(address: A): Option[C]
}

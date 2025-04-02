package stasis.client.api.clients

import stasis.core.discovery.providers.client.ServiceDiscoveryProvider

sealed trait Clients {
  def api: ServerApiEndpointClient
  def core: ServerCoreEndpointClient
}

object Clients {
  final case class Static(
    override val api: ServerApiEndpointClient,
    override val core: ServerCoreEndpointClient
  ) extends Clients

  final case class Discovered(
    discovery: ServiceDiscoveryProvider
  ) extends Clients {
    override def api: ServerApiEndpointClient = discovery.latest[ServerApiEndpointClient]
    override def core: ServerCoreEndpointClient = discovery.latest[ServerCoreEndpointClient]
  }

  def apply(api: ServerApiEndpointClient, core: ServerCoreEndpointClient): Clients =
    Clients.Static(api = api, core = core)

  def apply(discovery: ServiceDiscoveryProvider): Clients =
    Clients.Discovered(discovery = discovery)
}

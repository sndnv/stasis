package stasis.client.api.clients

final case class Clients(
  api: ServerApiEndpointClient,
  core: ServerCoreEndpointClient
)

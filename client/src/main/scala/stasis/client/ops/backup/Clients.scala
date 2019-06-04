package stasis.client.ops.backup

import stasis.client.api.{ServerApiEndpointClient, ServerCoreEndpointClient}

final case class Clients(
  api: ServerApiEndpointClient,
  core: ServerCoreEndpointClient
)

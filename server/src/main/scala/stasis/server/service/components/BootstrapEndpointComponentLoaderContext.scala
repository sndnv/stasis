package stasis.server.service.components

import io.github.sndnv.layers.events.EventCollector

import stasis.server.security.CredentialsManagers
import stasis.server.security.ResourceProvider
import stasis.server.service.ServerPersistence

final case class BootstrapEndpointComponentLoaderContext(
  base: DefaultComponentContext,
  authenticators: Authenticators,
  credentialsManagers: CredentialsManagers,
  eventCollector: EventCollector,
  serverPersistence: ServerPersistence,
  resourceProvider: ResourceProvider
) {
  val components: (Authenticators, CredentialsManagers, EventCollector, ServerPersistence, ResourceProvider) =
    (authenticators, credentialsManagers, eventCollector, serverPersistence, resourceProvider)
}

object BootstrapEndpointComponentLoaderContext {
  implicit def componentsToContext(implicit
    base: DefaultComponentContext,
    authenticators: Authenticators,
    credentialsManagers: CredentialsManagers,
    eventCollector: EventCollector,
    serverPersistence: ServerPersistence,
    resourceProvider: ResourceProvider
  ): BootstrapEndpointComponentLoaderContext = new BootstrapEndpointComponentLoaderContext(
    base = base,
    authenticators = authenticators,
    credentialsManagers = credentialsManagers,
    eventCollector = eventCollector,
    serverPersistence = serverPersistence,
    resourceProvider = resourceProvider
  )
}

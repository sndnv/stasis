package stasis.core.discovery

trait ServiceApiClient

object ServiceApiClient {
  trait Factory {
    def create(endpoint: ServiceApiEndpoint.Api, coreClient: ServiceApiClient): ServiceApiClient
    def create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient
    def create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient
  }
}

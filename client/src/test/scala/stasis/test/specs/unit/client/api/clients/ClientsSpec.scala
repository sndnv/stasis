package stasis.test.specs.unit.client.api.clients

import scala.reflect.ClassTag

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.client.api.clients.Clients
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.core.discovery.ServiceApiClient
import stasis.core.discovery.providers.client.ServiceDiscoveryProvider
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpointClient

class ClientsSpec extends AsyncUnitSpec {
  "Clients" should "provide static and discovery-based clients" in {
    val staticApiClient = MockServerApiEndpointClient()
    val staticCoreClient = MockServerCoreEndpointClient()

    val discoveryProvider = new ServiceDiscoveryProvider {
      override def latest[T <: ServiceApiClient](implicit tag: ClassTag[T]): T =
        if (tag.runtimeClass == classOf[ServerApiEndpointClient]) {
          MockServerApiEndpointClient().asInstanceOf[T]
        } else {
          MockServerCoreEndpointClient().asInstanceOf[T]
        }
    }

    val static = Clients(api = staticApiClient, core = staticCoreClient)
    static should be(a[Clients.Static])
    static.api should be(staticApiClient)
    static.core should be(staticCoreClient)

    val discovery = Clients(discovery = discoveryProvider)
    discovery should be(a[Clients.Discovered])
    discovery.api should not be staticApiClient
    discovery.core should not be staticCoreClient
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ClientsSpec"
  )
}

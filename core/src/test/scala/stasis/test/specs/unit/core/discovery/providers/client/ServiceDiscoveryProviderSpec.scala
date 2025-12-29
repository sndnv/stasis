package stasis.test.specs.unit.core.discovery.providers.client

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually

import stasis.core.discovery.ServiceApiClient
import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryClient
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.discovery.exceptions.DiscoveryFailure
import stasis.core.discovery.providers.client.ServiceDiscoveryProvider
import stasis.core.networking.http.HttpEndpointAddress
import io.github.sndnv.layers.testing.UnitSpec
import stasis.test.specs.unit.core.discovery.mocks.MockServiceDiscoveryClient
import stasis.test.specs.unit.core.discovery.providers.client.ServiceDiscoveryProviderSpec.TestApiClient
import stasis.test.specs.unit.core.discovery.providers.client.ServiceDiscoveryProviderSpec.TestCoreClient

class ServiceDiscoveryProviderSpec extends UnitSpec with Eventually with BeforeAndAfterAll {
  "A ServiceDiscoveryProvider" should "support providing existing clients when discovery is not active" in {
    val provider = ServiceDiscoveryProvider(
      interval = 3.seconds,
      initialClients = createClients(
        initialDiscoveryResult = ServiceDiscoveryResult.KeepExisting,
        nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
      ),
      clientFactory = createClientFactory()
    ).await

    provider should be(a[ServiceDiscoveryProvider.Disabled])

    provider.latest[ServiceDiscoveryClient] should be(a[MockServiceDiscoveryClient])
    a[DiscoveryFailure] should be thrownBy provider.latest[TestApiClient]
    a[DiscoveryFailure] should be thrownBy provider.latest[TestCoreClient]
  }

  it should "support providing new clients when discovery is active" in {
    val provider = ServiceDiscoveryProvider(
      interval = 3.seconds,
      initialClients = createClients(
        initialDiscoveryResult = ServiceDiscoveryResult.SwitchTo(
          endpoints = ServiceDiscoveryResult.Endpoints(
            api = ServiceApiEndpoint.Api(uri = "test-rui"),
            core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "test-rui")),
            discovery = ServiceApiEndpoint.Discovery(uri = "test-rui")
          ),
          recreateExisting = false
        ),
        nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
      ),
      clientFactory = createClientFactory()
    ).await

    provider should be(a[ServiceDiscoveryProvider.Default])

    provider.latest[ServiceDiscoveryClient] should be(a[MockServiceDiscoveryClient])
    noException should be thrownBy provider.latest[TestApiClient]
    noException should be thrownBy provider.latest[TestCoreClient]
  }

  it should "periodically refresh list of endpoints and clients (with result=keep-existing)" in {
    def createResult(recreateExisting: Boolean = false): ServiceDiscoveryResult = ServiceDiscoveryResult.SwitchTo(
      endpoints = ServiceDiscoveryResult.Endpoints(
        api = ServiceApiEndpoint.Api(uri = java.util.UUID.randomUUID().toString),
        core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = java.util.UUID.randomUUID().toString)),
        discovery = ServiceApiEndpoint.Discovery(uri = java.util.UUID.randomUUID().toString)
      ),
      recreateExisting = recreateExisting
    )

    val provider = ServiceDiscoveryProvider(
      interval = 200.millis,
      initialClients = createClients(initialDiscoveryResult = createResult(), nextDiscoveryResult = createResult()),
      clientFactory = createClientFactory()
    ).await

    provider should be(a[ServiceDiscoveryProvider.Default])

    val initialDiscoveryClient = provider.latest[ServiceDiscoveryClient]
    val initialApiClient = provider.latest[TestApiClient]
    val initialCoreClient = provider.latest[TestCoreClient]

    await(delay = 100.millis)

    provider.latest[ServiceDiscoveryClient] should be(initialDiscoveryClient)
    provider.latest[TestApiClient] should be(initialApiClient)
    provider.latest[TestCoreClient] should be(initialCoreClient)
  }

  it should "periodically refresh list of endpoints and clients (with result=switch-to)" in {
    def createResult(recreateExisting: Boolean = false): ServiceDiscoveryResult = ServiceDiscoveryResult.SwitchTo(
      endpoints = ServiceDiscoveryResult.Endpoints(
        api = ServiceApiEndpoint.Api(uri = java.util.UUID.randomUUID().toString),
        core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = java.util.UUID.randomUUID().toString)),
        discovery = ServiceApiEndpoint.Discovery(uri = java.util.UUID.randomUUID().toString)
      ),
      recreateExisting = recreateExisting
    )

    val provider = ServiceDiscoveryProvider(
      interval = 200.millis,
      initialClients = createClients(initialDiscoveryResult = createResult(), nextDiscoveryResult = createResult()),
      clientFactory = createClientFactory(nextDiscoveryResult = createResult(recreateExisting = true))
    ).await

    provider should be(a[ServiceDiscoveryProvider.Default])

    val initialDiscoveryClient = provider.latest[ServiceDiscoveryClient]
    val initialApiClient = provider.latest[TestApiClient]
    val initialCoreClient = provider.latest[TestCoreClient]

    await(delay = 100.millis)

    provider.latest[ServiceDiscoveryClient] should be(initialDiscoveryClient)
    provider.latest[TestApiClient] should be(initialApiClient)
    provider.latest[TestCoreClient] should be(initialCoreClient)

    eventually[Assertion] {
      provider.latest[ServiceDiscoveryClient] should not be initialDiscoveryClient
      provider.latest[TestApiClient] should not be initialApiClient
      provider.latest[TestCoreClient] should not be initialCoreClient
    }
  }

  it should "not recreate clients for same endpoints" in {
    def createResult(): ServiceDiscoveryResult = ServiceDiscoveryResult.SwitchTo(
      endpoints = ServiceDiscoveryResult.Endpoints(
        api = ServiceApiEndpoint.Api(uri = java.util.UUID.randomUUID().toString),
        core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = java.util.UUID.randomUUID().toString)),
        discovery = ServiceApiEndpoint.Discovery(uri = java.util.UUID.randomUUID().toString)
      ),
      recreateExisting = false
    )

    val provider = ServiceDiscoveryProvider(
      interval = 100.millis,
      initialClients = createClients(initialDiscoveryResult = createResult(), nextDiscoveryResult = createResult()),
      clientFactory = createClientFactory(nextDiscoveryResult = createResult())
    ).await

    provider should be(a[ServiceDiscoveryProvider.Default])

    val initialDiscoveryClient = provider.latest[ServiceDiscoveryClient]
    val initialApiClient = provider.latest[TestApiClient]
    val initialCoreClient = provider.latest[TestCoreClient]

    await(delay = 200.millis)

    provider.latest[ServiceDiscoveryClient] should not be initialDiscoveryClient
    provider.latest[TestApiClient] should not be initialApiClient
    provider.latest[TestCoreClient] should not be initialCoreClient

    val latestDiscoveryClient = provider.latest[ServiceDiscoveryClient]
    val latestApiClient = provider.latest[TestApiClient]
    val latestCoreClient = provider.latest[TestCoreClient]

    await(delay = 200.millis)

    eventually[Assertion] {
      latestDiscoveryClient should not be initialDiscoveryClient
      latestApiClient should not be initialApiClient
      latestCoreClient should not be initialCoreClient

      provider.latest[ServiceDiscoveryClient] should be(latestDiscoveryClient)
      provider.latest[TestApiClient] should be(latestApiClient)
      provider.latest[TestCoreClient] should be(latestCoreClient)
    }
  }

  it should "fail to retrieve unsupported client types" in {
    val provider = ServiceDiscoveryProvider(
      interval = 3.seconds,
      initialClients = createClients(
        initialDiscoveryResult = ServiceDiscoveryResult.KeepExisting,
        nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
      ),
      clientFactory = createClientFactory()
    ).await

    provider should be(a[ServiceDiscoveryProvider.Disabled])

    val e = intercept[DiscoveryFailure](provider.latest[TestApiClient])
    e.getMessage should be(s"Service client [${classOf[TestApiClient].getName}] was not found")
  }

  it should "handle discovery failures" in {
    val clientCalls = new AtomicInteger(0)

    val _ = ServiceDiscoveryProvider(
      interval = 200.millis,
      initialClients = createClients(
        initialDiscoveryResult = ServiceDiscoveryResult.SwitchTo(
          endpoints = ServiceDiscoveryResult.Endpoints(
            api = ServiceApiEndpoint.Api(uri = "test-rui"),
            core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "test-rui")),
            discovery = ServiceApiEndpoint.Discovery(uri = "test-rui")
          ),
          recreateExisting = false
        ),
        nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
      ),
      clientFactory = new ServiceApiClient.Factory {
        override def create(endpoint: ServiceApiEndpoint.Api, coreClient: ServiceApiClient): ServiceApiClient =
          new TestApiClient {}

        override def create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient =
          new TestCoreClient {}

        override def create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient =
          new ServiceDiscoveryClient {
            override val attributes: ServiceDiscoveryClient.Attributes =
              MockServiceDiscoveryClient.TestAttributes(a = "b")

            override def latest(isInitialRequest: Boolean): Future[ServiceDiscoveryResult] = {
              val _ = clientCalls.incrementAndGet()
              Future.failed(new RuntimeException("Test failure"))
            }
          }
      }
    ).await

    clientCalls.get() should be(0)

    await(delay = 100.millis)

    clientCalls.get() should be(0)

    await(delay = 300.millis)

    eventually {
      clientCalls.get() should be >= 5 // the interval is reduced so more requests should be made
    }
  }

  private def createClients(
    initialDiscoveryResult: ServiceDiscoveryResult,
    nextDiscoveryResult: ServiceDiscoveryResult
  ): Seq[ServiceApiClient] = Seq(
    new MockServiceDiscoveryClient(
      initialDiscoveryResult = initialDiscoveryResult,
      nextDiscoveryResult = nextDiscoveryResult
    )
  )

  private def createClientFactory(
    nextDiscoveryResult: ServiceDiscoveryResult = ServiceDiscoveryResult.KeepExisting
  ): ServiceApiClient.Factory =
    new ServiceApiClient.Factory {
      override def create(endpoint: ServiceApiEndpoint.Api, coreClient: ServiceApiClient): ServiceApiClient =
        new TestApiClient {}

      override def create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient =
        new TestCoreClient {}

      override def create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient =
        new MockServiceDiscoveryClient(
          initialDiscoveryResult = nextDiscoveryResult,
          nextDiscoveryResult = nextDiscoveryResult
        )
    }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ServiceDiscoveryProviderSpec"
  )

  override protected def afterAll(): Unit = typedSystem.terminate()
}

object ServiceDiscoveryProviderSpec {
  class TestApiClient extends ServiceApiClient
  class TestCoreClient extends ServiceApiClient
}

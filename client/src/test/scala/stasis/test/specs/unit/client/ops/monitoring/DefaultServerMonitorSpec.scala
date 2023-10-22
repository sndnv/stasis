package stasis.test.specs.unit.client.ops.monitoring

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.scalatest.concurrent.Eventually
import org.scalatest.{Assertion, BeforeAndAfterAll}
import stasis.client.ops.monitoring.DefaultServerMonitor
import stasis.shared.api.responses.Ping
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.{MockServerApiEndpointClient, MockServerTracker}

import scala.concurrent.Future
import scala.concurrent.duration._

class DefaultServerMonitorSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll {
  "A DefaultServerMonitor" should "support pinging servers periodically" in {
    val mockApiClient = MockServerApiEndpointClient()
    val mockTracker = MockServerTracker()

    val initialDelay = 100.millis

    val monitor = createMonitor(
      initialDelay = initialDelay,
      interval = defaultInterval,
      api = mockApiClient,
      tracker = mockTracker
    )

    managedMonitor(monitor) {
      await(initialDelay / 2, withSystem = typedSystem)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

      mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be(0)
      mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be(0)

      await(initialDelay, withSystem = typedSystem)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(1)

      mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be(1)
      mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be(0)

      await(defaultInterval, withSystem = typedSystem)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be >= 2

      mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be >= 2
      mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be(0)
    }
  }

  it should "handle ping failures" in {
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def ping(): Future[Ping] = Future.failed(new RuntimeException("test failure"))
    }
    val mockTracker = MockServerTracker()
    val monitor = createMonitor(
      initialDelay = defaultInterval / 2,
      interval = defaultInterval / 2,
      api = mockApiClient,
      tracker = mockTracker
    )

    managedMonitor(monitor) {
      await(defaultInterval, withSystem = typedSystem)

      mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be(0)
      mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be >= 1
    }
  }

  it should "support stopping itself" in {
    val mockTracker = MockServerTracker()
    val monitor = createMonitor(
      initialDelay = defaultInterval / 2,
      interval = defaultInterval / 2,
      api = MockServerApiEndpointClient(),
      tracker = mockTracker
    )

    eventually[Assertion] {
      mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be(1)
      mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be(0)
    }

    val _ = monitor.stop().await

    await(defaultInterval, withSystem = typedSystem)

    mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be(1)
    mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be(0)
  }

  private def createMonitor(
    initialDelay: FiniteDuration,
    interval: FiniteDuration,
    api: MockServerApiEndpointClient,
    tracker: MockServerTracker
  ): DefaultServerMonitor =
    DefaultServerMonitor(
      initialDelay = initialDelay,
      interval = interval,
      api = api,
      tracker = tracker
    )

  private def managedMonitor(monitor: DefaultServerMonitor)(block: => Assertion): Assertion =
    try {
      block
    } finally {
      val _ = monitor.stop().await
    }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(7.seconds, 300.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultServerMonitorSpec"
  )

  private val defaultInterval: FiniteDuration = 200.millis

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}

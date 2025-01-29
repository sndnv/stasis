package stasis.test.specs.unit.client.ops.monitoring

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually

import stasis.client.ops.monitoring.DefaultServerMonitor
import stasis.shared.api.responses.Ping
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.client.mocks.MockServerTracker

class DefaultServerMonitorSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll {
  "A DefaultServerMonitor" should "support calculating a full collection interval" in withRetry {
    implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

    (0 to 10000).foreach { _ =>
      val generated = DefaultServerMonitor.fullInterval(interval = 1000.millis)
      generated.toMillis should (be >= 900L and be < 1100L)
    }

    succeed
  }

  it should "support calculating a partial collection interval" in withRetry {
    implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

    (0 to 10000).foreach { _ =>
      val generated = DefaultServerMonitor
        .reducedInterval(
          interval = 1000.millis,
          initialDelay = 100.millis
        )

      // the reduced interval is 10x smaller than the original but the minimum is 100ms
      generated.toMillis should (be >= 100L and be < 110L)
    }

    succeed
  }

  it should "support pinging servers periodically" in {
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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)

      mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be(0)
      mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be(0)

      await(initialDelay, withSystem = typedSystem)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)

      mockTracker.statistics(MockServerTracker.Statistic.ServerReachable) should be(1)
      mockTracker.statistics(MockServerTracker.Statistic.ServerUnreachable) should be(0)

      await(defaultInterval, withSystem = typedSystem)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be >= 2
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)

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

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultServerMonitorSpec"
  )

  private val defaultInterval: FiniteDuration = 200.millis

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}

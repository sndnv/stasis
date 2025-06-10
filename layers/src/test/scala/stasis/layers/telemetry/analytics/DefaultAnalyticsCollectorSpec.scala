package stasis.layers.telemetry.analytics

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually

import stasis.layers.UnitSpec
import stasis.layers.telemetry.ApplicationInformation

class DefaultAnalyticsCollectorSpec extends UnitSpec with Eventually {
  "A DefaultAnalyticsCollector" should "record events" in {
    val persistence = MockAnalyticsPersistence()

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    collector.recordEvent("test_event", "a" -> "b")
    collector.recordEvent("test_event", "a" -> "b", "c" -> "d")
    collector.recordEvent("test_event", Map("a" -> "b"))

    collector.state.map { state =>
      state.events.toList match {
        case event1 :: event2 :: event3 :: event4 :: Nil =>
          event1.id should be(0)
          event1.event should be("test_event")

          event2.id should be(1)
          event2.event should be("test_event{a='b'}")

          event3.id should be(2)
          event3.event should be("test_event{a='b',c='d'}")

          event4.id should be(3)
          event4.event should be("test_event{a='b'}")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      state.failures should be(empty)
    }
  }

  it should "record failures" in {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def lastTransmitted: Instant = Instant.now() // prevents transmission
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordFailure(new RuntimeException("Test failure"))
    collector.recordFailure("Other failure")

    collector.state.map { state =>
      state.events should be(empty)

      state.failures.toList match {
        case failure1 :: failure2 :: Nil =>
          failure1.message should be("RuntimeException - Test failure")
          failure2.message should be("Other failure")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "support loading cache state" in {
    val persistence = MockAnalyticsPersistence(
      existing = AnalyticsEntry
        .collected(app = ApplicationInformation.none)
        .withEvent(name = "existing_event", attributes = Map.empty)
        .withFailure(message = "Existing failure")
    )

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")

    collector.state.map { state =>
      state.events.toList match {
        case event1 :: event2 :: Nil =>
          event1.id should be(0)
          event1.event should be("existing_event")

          event2.id should be(1)
          event2.event should be("test_event")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      state.failures.map(_.message) should be(Seq("Existing failure"))

      persistence.cached should be(empty)
      persistence.transmitted should be(empty)
    }
  }

  it should "handle failures when loading cached state" in {
    val persistence = MockAnalyticsPersistence(existing = new RuntimeException("Test failure"))

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.state.map { state =>
      state.events should be(empty)
      state.failures should be(empty)

      persistence.cached should be(empty)
      persistence.transmitted should be(empty)
    }
  }

  it should "support caching state locally" in {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def lastTransmitted: Instant = Instant.now() // prevents transmission
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config.copy(persistenceInterval = 100.millis),
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    await(delay = 75.millis, withSystem = typedSystem)
    collector.recordEvent("test_event", "a" -> "b")
    await(delay = 75.millis, withSystem = typedSystem)
    collector.recordEvent("test_event", "a" -> "b", "c" -> "d")
    await(delay = 75.millis, withSystem = typedSystem)
    collector.recordEvent("test_event", Map("a" -> "b"))
    await(delay = 150.millis, withSystem = typedSystem)
    collector.recordFailure(message = "Test failure")

    collector.state.map { state =>
      state.events.toList match {
        case event1 :: event2 :: event3 :: event4 :: Nil =>
          event1.id should be(0)
          event1.event should be("test_event")

          event2.id should be(1)
          event2.event should be("test_event{a='b'}")

          event3.id should be(2)
          event3.event should be("test_event{a='b',c='d'}")

          event4.id should be(3)
          event4.event should be("test_event{a='b'}")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      state.failures.map(_.message) should be(Seq("Test failure"))

      persistence.cached.toList match {
        case firstCached :: secondCached :: failureCached :: Nil =>
          firstCached.events.size should be(2)
          firstCached.failures should be(empty)

          secondCached.events.size should be(4)
          secondCached.failures should be(empty)

          failureCached.events.size should be(4)
          failureCached.failures.map(_.message) should be(Seq("Test failure"))

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      persistence.transmitted should be(empty)
    }
  }

  it should "support transmitting state remotely" in {
    val persistence = MockAnalyticsPersistence()

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    collector.recordFailure(message = "Test failure")

    eventually {
      collector.state.map { state =>
        state.events should be(empty)
        state.failures should be(empty)

        persistence.cached.toList match {
          case clearCached :: Nil =>
            clearCached.events should be(empty)
            clearCached.failures should be(empty)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted.toList match {
          case transmitted :: Nil =>
            transmitted.events.size should be(1)
            transmitted.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "cache messages while transmitting" in {
    val transmissionStarted: AtomicBoolean = new AtomicBoolean(false)

    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def transmit(entry: AnalyticsEntry): Future[Done] = {
        transmissionStarted.set(true)
        await(delay = 250.millis, withSystem = typedSystem)
        super.transmit(entry)
      }
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordFailure(message = "Test failure")

    eventually {
      transmissionStarted.get() should be(true)
    }

    collector.recordEvent("test_event")
    collector.recordEvent("test_event")
    collector.recordEvent("test_event")
    collector.recordFailure(message = "Test failure")

    eventually {
      collector.state.map { state =>
        state.events.size should be(3)
        state.failures.size should be(1)

        persistence.cached.toList match {
          case clearCached :: failureCached :: Nil =>
            clearCached.events should be(empty)
            clearCached.failures should be(empty)

            failureCached.events.size should be(3)
            failureCached.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted.toList match {
          case transmitted :: Nil =>
            transmitted.events should be(empty)
            transmitted.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "handle transmission failures" in {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def transmit(entry: AnalyticsEntry): Future[Done] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    collector.recordFailure(message = "Test failure")

    eventually {
      collector.state.map { state =>
        state.events.toList match {
          case event1 :: Nil =>
            event1.id should be(0)
            event1.event should be("test_event")

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        state.failures.map(_.message) should be(Seq("Test failure"))

        persistence.cached.toList match {
          case pendingCached :: Nil =>
            pendingCached.events.size should be(1)
            pendingCached.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted should be(empty)
      }
    }
  }

  it should "cache state during termination" in {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def lastTransmitted: Instant = Instant.now() // prevents transmission
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config.copy(persistenceInterval = config.persistenceInterval.mul(10L)),
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")

    persistence.cached should be(empty)
    persistence.transmitted should be(empty)

    collector.stop()

    eventually {
      persistence.cached.size should be(1)
      persistence.transmitted should be(empty)
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultAnalyticsCollectorSpec"
  )

  private val config = DefaultAnalyticsCollector.Config(
    persistenceInterval = 3.seconds,
    transmissionInterval = 10.minutes
  )
}

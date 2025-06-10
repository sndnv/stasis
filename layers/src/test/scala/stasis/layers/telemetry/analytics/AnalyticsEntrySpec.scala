package stasis.layers.telemetry.analytics

import java.time.Instant

import stasis.layers.UnitSpec
import stasis.layers.telemetry.ApplicationInformation
import stasis.layers.telemetry.analytics.AnalyticsEntrySpec.TestAnalyticsEntry

class AnalyticsEntrySpec extends UnitSpec {
  "An AnalyticsEntry" should "support converting to a collected entry" in {
    val now = Instant.now()

    val testEntry = TestAnalyticsEntry(
      id = "test-id",
      runtime = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none),
      events = Seq.empty,
      failures = Seq.empty,
      created = now,
      updated = now
    )

    val collectedEntry = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .copy(
        created = now,
        updated = now
      )

    testEntry.asCollected() should be(collectedEntry)

    collectedEntry.asCollected() should be(collectedEntry)
  }

  "A Collected AnalyticsEntry" should "support adding events" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withEvent(name = "test_event", attributes = Map.empty)
      .withEvent(name = "test_event", attributes = Map("a" -> "b"))
      .withEvent(name = "test_event", attributes = Map("c" -> "d", "a" -> "b"))

    updated.events should not be empty
    updated.failures should be(empty)

    updated.events.toList match {
      case event1 :: event2 :: event3 :: Nil =>
        event1.id should be(0)
        event1.event should be("test_event")

        event2.id should be(1)
        event2.event should be("test_event{a='b'}")

        event3.id should be(2)
        event3.event should be("test_event{a='b',c='d'}")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support adding failures" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withFailure(message = "Test failure #1")
      .withFailure(message = "Test failure #2")
      .withFailure(message = "Test failure #3")

    updated.events should be(empty)
    updated.failures should not be empty

    updated.failures.toList match {
      case failure1 :: failure2 :: failure3 :: Nil =>
        failure1.message should be("Test failure #1")
        failure2.message should be("Test failure #2")
        failure3.message should be("Test failure #3")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support discarding all events" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withEvent(name = "test_event", attributes = Map.empty)
      .withEvent(name = "test_event", attributes = Map("a" -> "b"))
      .withEvent(name = "test_event", attributes = Map("c" -> "d", "a" -> "b"))

    updated.events should not be empty
    updated.failures should be(empty)

    val discarded = updated.discardEvents()

    discarded.events should be(empty)
    discarded.failures should be(empty)
  }

  it should "support discarding all failures" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withFailure(message = "Test failure #1")
      .withFailure(message = "Test failure #2")
      .withFailure(message = "Test failure #3")

    updated.events should be(empty)
    updated.failures should not be empty

    val discarded = updated.discardFailures()

    discarded.events should be(empty)
    discarded.failures should be(empty)
  }

  it should "support providing runtime information (without application information)" in {
    val information = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none)

    information.id should not be empty
    information.app should be("none;none;0")
    information.jre should not be empty
    information.os should not be empty
  }

  it should "support providing runtime information (with application information)" in {
    val information = AnalyticsEntry.RuntimeInformation(app = new ApplicationInformation {
      override def name: String = "test-name"
      override def version: String = "test-version"
      override def buildTime: Long = 42L
    })

    information.id should not be empty
    information.app should be("test-name;test-version;42")
    information.jre should not be empty
    information.os should not be empty
  }
}

object AnalyticsEntrySpec {
  final case class TestAnalyticsEntry(
    id: String,
    override val runtime: AnalyticsEntry.RuntimeInformation,
    override val events: Seq[AnalyticsEntry.Event],
    override val failures: Seq[AnalyticsEntry.Failure],
    override val created: Instant,
    override val updated: Instant
  ) extends AnalyticsEntry
}

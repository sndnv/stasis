package stasis.layers.telemetry.analytics

import stasis.layers.UnitSpec

class AnalyticsCollectorSpec extends UnitSpec {
  "A NoOp AnalyticsCollector" should "record nothing" in {
    val collector = AnalyticsCollector.NoOp

    collector.recordEvent("test_event")
    collector.recordEvent("test_event", "a" -> "b")
    collector.recordEvent("test_event", "a" -> "b", "c" -> "d")
    collector.recordEvent("test_event", Map("a" -> "b"))

    collector.recordFailure(new RuntimeException("Test failure"))
    collector.recordFailure("Other failure")

    collector.persistence should be(empty)

    for {
      state <- collector.state
    } yield {
      noException should be thrownBy collector.send()
      state.events should be(empty)
      state.failures should be(empty)
    }
  }
}

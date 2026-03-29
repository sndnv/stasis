package stasis.test.client_android.lib.telemetry.analytics

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import java.io.FileNotFoundException
import java.time.Instant

class AnalyticsEntrySpec : WordSpec({
    "An AnalyticsEntry" should {
        data class TestAnalyticsEntry(
            val id: String,
            override val runtime: AnalyticsEntry.RuntimeInformation,
            override val events: List<AnalyticsEntry.Event>,
            override val failures: List<AnalyticsEntry.Failure>,
            override val created: Instant,
            override val updated: Instant
        ) : AnalyticsEntry {
            override fun asJson(): AnalyticsEntry.AsJson = AnalyticsEntry.AsJson(
                entryType = "test",
                runtime = runtime,
                events = events,
                failures = failures,
                created = created,
                updated = updated
            )
        }

        "support converting to a collected entry" {
            val now = Instant.now()

            val testEntry = TestAnalyticsEntry(
                id = "test-id",
                runtime = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none()),
                events = emptyList(),
                failures = emptyList(),
                created = now,
                updated = now
            )

            val collectedEntry = AnalyticsEntry
                .collected(app = ApplicationInformation.none())
                .copy(
                    created = now,
                    updated = now
                )

            testEntry.asCollected() shouldBe (collectedEntry)

            collectedEntry.asCollected() shouldBe (collectedEntry)
        }

        "support converting to a json entry" {
            val now = Instant.now()

            val testEntry = TestAnalyticsEntry(
                id = "test-id",
                runtime = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none()),
                events = emptyList(),
                failures = emptyList(),
                created = now,
                updated = now
            )

            val jsonEntry = AnalyticsEntry.AsJson(
                entryType = "test",
                runtime = testEntry.runtime,
                events = testEntry.events,
                failures = testEntry.failures,
                created = testEntry.created,
                updated = testEntry.updated,
            )

            testEntry.asJson() shouldBe (jsonEntry)
        }
    }

    "A Collected AnalyticsEntry" should {
        "support converting to a json entry" {
            val collectedEntry = AnalyticsEntry.collected(app = ApplicationInformation.none())

            val jsonEntry = AnalyticsEntry.AsJson(
                entryType = "collected",
                runtime = collectedEntry.runtime,
                events = collectedEntry.events,
                failures = collectedEntry.failures,
                created = collectedEntry.created,
                updated = collectedEntry.updated,
            )

            collectedEntry.asJson() shouldBe (jsonEntry)
        }

        "support adding events" {
            val original = AnalyticsEntry.collected(app = ApplicationInformation.none())

            original.events.isEmpty() shouldBe (true)
            original.failures.isEmpty() shouldBe (true)

            val updated = original
                .withEvent(name = "test_event", attributes = emptyMap())
                .withEvent(name = "test_event", attributes = mapOf("a" to "b"))
                .withEvent(name = "test_event", attributes = mapOf("c" to "d", "a" to "b"))

            updated.events.isEmpty() shouldBe (false)
            updated.failures.isEmpty() shouldBe (true)

            val event1 = updated.events[0]
            val event2 = updated.events[1]
            val event3 = updated.events[2]

            event1.id shouldBe (0)
            event1.event shouldBe ("test_event")

            event2.id shouldBe (1)
            event2.event shouldBe ("test_event{a='b'}")

            event3.id shouldBe (2)
            event3.event shouldBe ("test_event{a='b',c='d'}")
        }

        "support adding failures" {
            val original = AnalyticsEntry.collected(app = ApplicationInformation.none())

            original.events.isEmpty() shouldBe (true)
            original.failures.isEmpty() shouldBe (true)

            val updated = original
                .withFailure(message = "Test failure #1", stackTrace = null)
                .withFailure(message = "Test failure #2", stackTrace = null)
                .withFailure(message = "Test failure #3", stackTrace = "abc")

            updated.events.isEmpty() shouldBe (true)
            updated.failures.isEmpty() shouldBe (false)

            val failure1 = updated.failures[0]
            val failure2 = updated.failures[1]
            val failure3 = updated.failures[2]

            failure1.message shouldBe ("Test failure #1")
            failure1.stackTrace shouldBe (null)

            failure2.message shouldBe ("Test failure #2")
            failure2.stackTrace shouldBe (null)

            failure3.message shouldBe ("Test failure #3")
            failure3.stackTrace shouldBe ("abc")
        }

        "support discarding all events" {
            val original = AnalyticsEntry.collected(app = ApplicationInformation.none())

            original.events.isEmpty() shouldBe (true)
            original.failures.isEmpty() shouldBe (true)

            val updated = original
                .withEvent(name = "test_event", attributes = emptyMap())
                .withEvent(name = "test_event", attributes = mapOf("a" to "b"))
                .withEvent(name = "test_event", attributes = mapOf("c" to "d", "a" to "b"))

            updated.events.isEmpty() shouldBe (false)
            updated.failures.isEmpty() shouldBe (true)

            val discarded = updated.discardEvents()

            discarded.events.isEmpty() shouldBe (true)
            discarded.failures.isEmpty() shouldBe (true)
        }

        "support discarding all failures" {
            val original = AnalyticsEntry.collected(app = ApplicationInformation.none())

            original.events.isEmpty() shouldBe (true)
            original.failures.isEmpty() shouldBe (true)

            val updated = original
                .withFailure(message = "Test failure #1", stackTrace = null)
                .withFailure(message = "Test failure #2", stackTrace = null)
                .withFailure(message = "Test failure #3", stackTrace = null)

            updated.events.isEmpty() shouldBe (true)
            updated.failures.isEmpty() shouldBe (false)

            val discarded = updated.discardFailures()

            discarded.events.isEmpty() shouldBe (true)
            discarded.failures.isEmpty() shouldBe (true)
        }

        "support providing runtime information (without application information)" {
            val information = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none())

            information.id.isEmpty() shouldBe (false)
            information.app shouldBe ("none;none;0")
            information.jre.isEmpty() shouldBe (false)
            information.os.isEmpty() shouldBe (false)
        }

        "support providing runtime information (with application information)" {
            val information = AnalyticsEntry.RuntimeInformation(app = object : ApplicationInformation {
                override val name: String = "test-name"
                override val version: String = "test-version"
                override val buildTime: Long = 42L
            })

            information.id.isEmpty() shouldBe (false)
            information.app shouldBe ("test-name;test-version;42")
            information.jre.isEmpty() shouldBe (false)
            information.os.isEmpty() shouldBe (false)
        }

        "support creating failures with and without a stack trace" {
            val failureWithStacktrace = AnalyticsEntry.Failure(
                message = "Test failure #1",
                timestamp = Instant.now(),
                stackTrace = "abc"
            )

            val failureWithoutStacktrace = AnalyticsEntry.Failure(
                message = "Test failure #2",
                timestamp = Instant.now()
            )

            failureWithStacktrace.message shouldBe ("Test failure #1")
            failureWithStacktrace.stackTrace shouldBe ("abc")

            failureWithoutStacktrace.message shouldBe ("Test failure #2")
            failureWithoutStacktrace.stackTrace shouldBe (null)
        }

        "support anonymizing failure content (paths)" {
            AnalyticsEntry.Failure.anonymize(content = "") shouldBe ("")

            AnalyticsEntry.Failure.anonymize(content = "Test failure") shouldBe ("Test failure")

            AnalyticsEntry.Failure.anonymize(content = "/x/y/z") shouldBe ("*CONTENT_REMOVED*")

            AnalyticsEntry.Failure.anonymize(
                content = "Permission denied: '/a/b/c' [Errno 13]"
            ) shouldBe ("Permission denied: ' *CONTENT_REMOVED* ' [Errno 13]")

            AnalyticsEntry.Failure.anonymize(
                content = "Permission denied: /a/b/c [Errno 13]"
            ) shouldBe ("Permission denied: *CONTENT_REMOVED* [Errno 13]")

            AnalyticsEntry.Failure.anonymize(
                content = "Access to the path 'C:\\a\\b\\c' is denied"
            ) shouldBe ("Access to the path ' *CONTENT_REMOVED* ' is denied")

            AnalyticsEntry.Failure.anonymize(
                content = "Access to the path C:\\a\\b\\c is denied"
            ) shouldBe ("Access to the path *CONTENT_REMOVED*")

            AnalyticsEntry.Failure.anonymize(
                content = "java.io.FileNotFoundException: C:\\1\\2\\3"
            ) shouldBe ("java.io.FileNotFoundException: *CONTENT_REMOVED*")

            AnalyticsEntry.Failure.anonymize(
                content = "java.io.FileNotFoundException: /x/y/ (No such file or directory)"
            ) shouldBe ("java.io.FileNotFoundException: *CONTENT_REMOVED* (No such file or directory)")

            AnalyticsEntry.Failure.anonymize(
                content = "java.io.FileNotFoundException: /x/y/z (No such file or directory)"
            ) shouldBe ("java.io.FileNotFoundException: *CONTENT_REMOVED* (No such file or directory)")
        }

        "support anonymizing failures with and without a stack trace" {
            val original = AnalyticsEntry.collected(app = ApplicationInformation.none())

            original.events.isEmpty() shouldBe (true)
            original.failures.isEmpty() shouldBe (true)

            val e1 = FileNotFoundException("/x/y/x")
            val e2 = FileNotFoundException("C:\\a\\b\\c")

            val updated = original
                .withFailure(
                    message = "${e1.javaClass.simpleName} - ${e1.message}"
                )
                .withFailure(
                    message = "${e1.javaClass.simpleName} - ${e1.message}",
                    stackTrace = e1.stackTraceToString()
                )
                .withFailure(
                    message = "${e2.javaClass.simpleName} - ${e2.message}"
                )
                .withFailure(
                    message = "${e2.javaClass.simpleName} - ${e2.message}",
                    stackTrace = e2.stackTraceToString()
                )

            updated.events.isEmpty() shouldBe (true)
            updated.failures.isEmpty() shouldBe (false)

            val failure1 = updated.failures[0]
            val failure2 = updated.failures[1]
            val failure3 = updated.failures[2]
            val failure4 = updated.failures[3]

            failure1.message shouldBe ("FileNotFoundException - *CONTENT_REMOVED*")
            failure1.stackTrace shouldBe (null)

            failure2.message shouldBe ("FileNotFoundException - *CONTENT_REMOVED*")
            failure2.stackTrace shouldStartWith ("java.io.FileNotFoundException: *CONTENT_REMOVED*")

            failure3.message shouldBe ("FileNotFoundException - *CONTENT_REMOVED*")
            failure3.stackTrace shouldBe (null)

            failure4.message shouldBe ("FileNotFoundException - *CONTENT_REMOVED*")
            failure4.stackTrace shouldStartWith ("java.io.FileNotFoundException: *CONTENT_REMOVED*")

        }
    }
})

import Foundation
@testable import StasisClientLib
import Testing

@Suite("AnalyticsEntry")
struct AnalyticsEntryTests {
    @Test("supports converting an AsJson wrapper to a collected entry")
    func supportsAsJsonToCollected() {
        let now = Date()
        let runtime = AnalyticsEntry.RuntimeInformation(app: NoApplicationInformation())
        let json = AnalyticsEntry.AsJson(
            entryType: "test",
            runtime: runtime,
            events: [],
            failures: [],
            created: now,
            updated: now
        )
        let wrapped: AnalyticsEntry = .asJson(json)
        let collected = AnalyticsEntry.Collected(
            runtime: runtime,
            events: [],
            failures: [],
            created: now,
            updated: now
        )

        #expect(wrapped.asCollected() == collected)
        #expect((AnalyticsEntry.collected(collected)).asCollected() == collected)
    }

    @Test("supports converting a Collected entry to a json entry")
    func supportsCollectedToJson() {
        let collected = AnalyticsEntry.Collected(app: NoApplicationInformation())
        let wrapped: AnalyticsEntry = .collected(collected)

        let expected = AnalyticsEntry.AsJson(
            entryType: "collected",
            runtime: collected.runtime,
            events: collected.events,
            failures: collected.failures,
            created: collected.created,
            updated: collected.updated
        )

        #expect(wrapped.asJson() == expected)
    }

    @Test("supports adding events")
    func supportsAddingEvents() {
        let original = AnalyticsEntry.Collected(app: NoApplicationInformation())
        #expect(original.events.isEmpty)
        #expect(original.failures.isEmpty)

        let updated = original
            .withEvent(name: "test_event", attributes: [:])
            .withEvent(name: "test_event", attributes: ["a": "b"])
            .withEvent(name: "test_event", attributes: ["c": "d", "a": "b"])

        #expect(!updated.events.isEmpty)
        #expect(updated.failures.isEmpty)

        #expect(updated.events[0].id == 0)
        #expect(updated.events[0].event == "test_event")

        #expect(updated.events[1].id == 1)
        #expect(updated.events[1].event == "test_event{a='b'}")

        #expect(updated.events[2].id == 2)
        #expect(updated.events[2].event == "test_event{a='b',c='d'}")
    }

    @Test("supports adding failures")
    func supportsAddingFailures() {
        let original = AnalyticsEntry.Collected(app: NoApplicationInformation())
        #expect(original.events.isEmpty)
        #expect(original.failures.isEmpty)

        let updated = original
            .withFailure(message: "Test failure #1")
            .withFailure(message: "Test failure #2")
            .withFailure(message: "Test failure #3")

        #expect(updated.events.isEmpty)
        #expect(!updated.failures.isEmpty)

        #expect(updated.failures[0].message == "Test failure #1")
        #expect(updated.failures[1].message == "Test failure #2")
        #expect(updated.failures[2].message == "Test failure #3")
    }

    @Test("supports discarding all events")
    func supportsDiscardingEvents() {
        let original = AnalyticsEntry.Collected(app: NoApplicationInformation())
        let updated = original
            .withEvent(name: "test_event", attributes: [:])
            .withEvent(name: "test_event", attributes: ["a": "b"])
            .withEvent(name: "test_event", attributes: ["c": "d", "a": "b"])

        #expect(!updated.events.isEmpty)
        #expect(updated.failures.isEmpty)

        let discarded = updated.discardEvents()
        #expect(discarded.events.isEmpty)
        #expect(discarded.failures.isEmpty)
    }

    @Test("supports discarding all failures")
    func supportsDiscardingFailures() {
        let original = AnalyticsEntry.Collected(app: NoApplicationInformation())
        let updated = original
            .withFailure(message: "Test failure #1")
            .withFailure(message: "Test failure #2")
            .withFailure(message: "Test failure #3")

        #expect(updated.events.isEmpty)
        #expect(!updated.failures.isEmpty)

        let discarded = updated.discardFailures()
        #expect(discarded.events.isEmpty)
        #expect(discarded.failures.isEmpty)
    }

    @Test("provides runtime information (without application information)")
    func runtimeInformationNone() {
        let information = AnalyticsEntry.RuntimeInformation(app: NoApplicationInformation())
        #expect(!information.id.isEmpty)
        #expect(information.app == "none;none;0")
        #expect(information.jre == "none")
        #expect(!information.os.isEmpty)
    }

    @Test("provides runtime information (with application information)")
    func runtimeInformationCustom() {
        struct TestApp: ApplicationInformation {
            let name = "test-name"
            let version = "test-version"
            let buildTime: Int64 = 42
        }

        let information = AnalyticsEntry.RuntimeInformation(app: TestApp())
        #expect(!information.id.isEmpty)
        #expect(information.app == "test-name;test-version;42")
        #expect(information.jre == "none")
        #expect(!information.os.isEmpty)
    }

    @Test("anonymizes failure content (paths)")
    func anonymizesFailureContent() {
        #expect(AnalyticsEntry.Failure.anonymize("") == "")
        #expect(AnalyticsEntry.Failure.anonymize("Test failure") == "Test failure")
        #expect(AnalyticsEntry.Failure.anonymize("/x/y/z") == "*CONTENT_REMOVED*")

        #expect(
            AnalyticsEntry.Failure.anonymize("Permission denied: '/a/b/c' [Errno 13]")
                == "Permission denied: ' *CONTENT_REMOVED* ' [Errno 13]"
        )

        #expect(
            AnalyticsEntry.Failure.anonymize("Permission denied: /a/b/c [Errno 13]")
                == "Permission denied: *CONTENT_REMOVED* [Errno 13]"
        )

        #expect(
            AnalyticsEntry.Failure.anonymize("Access to the path 'C:\\a\\b\\c' is denied")
                == "Access to the path ' *CONTENT_REMOVED* ' is denied"
        )

        #expect(
            AnalyticsEntry.Failure.anonymize("Access to the path C:\\a\\b\\c is denied")
                == "Access to the path *CONTENT_REMOVED*"
        )

        #expect(
            AnalyticsEntry.Failure.anonymize("java.io.FileNotFoundException: C:\\1\\2\\3")
                == "java.io.FileNotFoundException: *CONTENT_REMOVED*"
        )

        #expect(
            AnalyticsEntry.Failure.anonymize(
                "java.io.FileNotFoundException: /x/y/ (No such file or directory)"
            ) == "java.io.FileNotFoundException: *CONTENT_REMOVED* (No such file or directory)"
        )

        #expect(
            AnalyticsEntry.Failure.anonymize(
                "java.io.FileNotFoundException: /x/y/z (No such file or directory)"
            ) == "java.io.FileNotFoundException: *CONTENT_REMOVED* (No such file or directory)"
        )
    }

    @Test("anonymizes failure messages when adding")
    func anonymizesAddedFailures() {
        let original = AnalyticsEntry.Collected(app: NoApplicationInformation())

        let updated = original
            .withFailure(message: "FileNotFoundError - /x/y/z")
            .withFailure(message: "FileNotFoundError - C:\\a\\b\\c")

        #expect(updated.failures.count == 2)
        #expect(updated.failures[0].message == "FileNotFoundError - *CONTENT_REMOVED*")
        #expect(updated.failures[1].message == "FileNotFoundError - *CONTENT_REMOVED*")
    }

    @Test("exposes the inner entry's properties for each case")
    func exposesInnerProperties() {
        let now = Date()
        let later = now.addingTimeInterval(60)
        let runtime = AnalyticsEntry.RuntimeInformation(app: NoApplicationInformation())
        let collected = AnalyticsEntry.Collected(
            runtime: runtime,
            events: [],
            failures: [],
            created: now,
            updated: later
        )
        let json = AnalyticsEntry.AsJson(
            entryType: "test",
            runtime: runtime,
            events: [],
            failures: [],
            created: now,
            updated: later
        )

        let wrappedCollected: AnalyticsEntry = .collected(collected)
        #expect(wrappedCollected.runtime == runtime)
        #expect(wrappedCollected.created == now)
        #expect(wrappedCollected.updated == later)

        let wrappedJson: AnalyticsEntry = .asJson(json)
        #expect(wrappedJson.runtime == runtime)
        #expect(wrappedJson.created == now)
        #expect(wrappedJson.updated == later)
    }
}

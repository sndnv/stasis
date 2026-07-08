import Foundation
@testable import StasisClient
import Testing

@Suite("Settings")
struct SettingsTests {
    @Test("parses known datetime formats")
    func parsesKnownFormats() throws {
        #expect(try Settings.parseDateTimeFormat("system") == .system)
        #expect(try Settings.parseDateTimeFormat("iso") == .iso)
    }

    @Test("rejects unknown datetime formats")
    func rejectsUnknown() {
        #expect(throws: SettingsError.self) { try Settings.parseDateTimeFormat("bogus") }
    }

    @Test("falls back to defaults when keys are unset")
    func defaultsWhenMissing() {
        let defaults = TestDefaults.isolatedDefaults()
        #expect(defaults.dateTimeFormat() == Settings.Defaults.dateTimeFormat)
        #expect(defaults.schedulingEnabled() == Settings.Defaults.schedulingEnabled)
        #expect(defaults.pingInterval() == Settings.Defaults.pingInterval)
        #expect(defaults.commandRefreshInterval() == Settings.Defaults.commandRefreshInterval)
        #expect(defaults.discoveryInterval() == Settings.Defaults.discoveryInterval)
        #expect(defaults.cacheActiveInterval() == Settings.Defaults.cacheActiveInterval)
        #expect(defaults.cachePendingInterval() == Settings.Defaults.cachePendingInterval)
        #expect(defaults.analyticsEnabled() == Settings.Defaults.analyticsEnabled)
        #expect(defaults.analyticsKeepEvents() == Settings.Defaults.analyticsKeepEvents)
        #expect(defaults.analyticsKeepFailures() == Settings.Defaults.analyticsKeepFailures)
        #expect(defaults.analyticsPersistenceInterval() == Settings.Defaults.analyticsPersistenceInterval)
        #expect(defaults.analyticsTransmissionInterval() == Settings.Defaults.analyticsTransmissionInterval)
    }

    @Test("reads stored values")
    func storedValues() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("iso", forKey: Settings.Keys.dateTimeFormat)
        defaults.set(false, forKey: Settings.Keys.schedulingEnabled)
        defaults.set("42", forKey: Settings.Keys.pingInterval)
        defaults.set(false, forKey: Settings.Keys.analyticsKeepFailures)

        #expect(defaults.dateTimeFormat() == .iso)
        #expect(!defaults.schedulingEnabled())
        #expect(defaults.pingInterval() == 42)
        #expect(!defaults.analyticsKeepFailures())
    }

    @Test("ignores unparseable numeric strings")
    func ignoresUnparseable() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("not-a-number", forKey: Settings.Keys.discoveryInterval)
        #expect(defaults.discoveryInterval() == Settings.Defaults.discoveryInterval)
    }

    @Test("reads user-defined commandRefreshInterval")
    func userDefinedCommandRefresh() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("120", forKey: Settings.Keys.commandRefreshInterval)
        #expect(defaults.commandRefreshInterval() == 120)
    }

    @Test("reads user-defined discoveryInterval")
    func userDefinedDiscovery() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("600", forKey: Settings.Keys.discoveryInterval)
        #expect(defaults.discoveryInterval() == 600)
    }

    @Test("reads user-defined cacheActiveInterval")
    func userDefinedCacheActive() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("45", forKey: Settings.Keys.cacheActiveInterval)
        #expect(defaults.cacheActiveInterval() == 45)
    }

    @Test("reads user-defined cachePendingInterval")
    func userDefinedCachePending() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("900", forKey: Settings.Keys.cachePendingInterval)
        #expect(defaults.cachePendingInterval() == 900)
    }

    @Test("reads user-defined analyticsPersistenceInterval")
    func userDefinedAnalyticsPersistence() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("180", forKey: Settings.Keys.analyticsPersistenceInterval)
        #expect(defaults.analyticsPersistenceInterval() == 180)
    }

    @Test("reads user-defined analyticsTransmissionInterval")
    func userDefinedAnalyticsTransmission() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set("1200", forKey: Settings.Keys.analyticsTransmissionInterval)
        #expect(defaults.analyticsTransmissionInterval() == 1200)
    }

    @Test("reads user-defined analyticsEnabled")
    func userDefinedAnalyticsEnabled() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set(false, forKey: Settings.Keys.analyticsEnabled)
        #expect(!defaults.analyticsEnabled())
    }

    @Test("reads user-defined analyticsKeepEvents")
    func userDefinedAnalyticsKeepEvents() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set(false, forKey: Settings.Keys.analyticsKeepEvents)
        #expect(!defaults.analyticsKeepEvents())
    }

    @Test("reads Double-encoded intervals written by @AppStorage")
    func intervalsFromAppStorageDouble() {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set(180.0 as Double, forKey: Settings.Keys.pingInterval)
        defaults.set(900.0 as Double, forKey: Settings.Keys.analyticsTransmissionInterval)
        #expect(defaults.pingInterval() == 180)
        #expect(defaults.analyticsTransmissionInterval() == 900)
    }
}

@Suite("SettingsError")
struct SettingsErrorTests {
    @Test("describes an unexpected date/time format")
    func message() {
        #expect(SettingsError.unexpectedDateTimeFormat("test").errorDescription == "Unexpected date/time format [test]")
    }
}

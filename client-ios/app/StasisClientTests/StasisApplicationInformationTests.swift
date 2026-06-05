import Foundation
@testable import StasisClient
import Testing

@Suite("StasisApplicationInformation")
struct StasisApplicationInformationTests {
    @Test("preserves explicit values")
    func preservesExplicitValues() {
        let info = StasisApplicationInformation(name: "stasis-test", version: "1.2.3", buildTime: 42)
        #expect(info.name == "stasis-test")
        #expect(info.version == "1.2.3")
        #expect(info.buildTime == 42)
    }

    @Test("reads CFBundleShortVersionString + bundleIdentifier from Bundle")
    func readsFromBundle() {
        let bundle = TestBundle(
            identifier: "stasis.client.ios.tests.alt",
            info: ["CFBundleShortVersionString": "9.8.7", "BuildTime": NSNumber(value: 1_700_000_000)]
        )
        let info = StasisApplicationInformation(bundle: bundle)
        #expect(info.name == "stasis.client.ios.tests.alt")
        #expect(info.version == "9.8.7")
        #expect(info.buildTime == 1_700_000_000)
    }

    @Test("falls back when bundle lacks identifier / version / build time")
    func fallsBack() {
        let bundle = TestBundle(identifier: nil, info: [:])
        let info = StasisApplicationInformation(bundle: bundle, fallbackName: "fallback-name")
        #expect(info.name == "fallback-name")
        #expect(info.version == "0.0.0")
        #expect(info.buildTime == 0)
    }
}

private final class TestBundle: Bundle, @unchecked Sendable {
    private let providedIdentifier: String?
    private let providedInfo: [String: Any]

    init(identifier: String?, info: [String: Any]) {
        self.providedIdentifier = identifier
        self.providedInfo = info
        super.init()
    }

    override var bundleIdentifier: String? { providedIdentifier }
    override var infoDictionary: [String: Any]? { providedInfo }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("CreateAnalyticsEntry interop")
struct CreateAnalyticsEntryInteropTests {
    @Test("decode and re-encode a request")
    func request() throws {
        try assert(
            domain: "analytics",
            resource: "CreateAnalyticsEntry",
            matches: CreateAnalyticsEntry(
                entry: AnalyticsEntry.AsJson(
                    entryType: "collected",
                    runtime: AnalyticsEntry.RuntimeInformation(
                        id: "10136ad4-c1c2-469b-94b9-79af1e2b939e",
                        app: "stasis-client;1.0.0",
                        jre: "none",
                        os: "linux;6.1.0;x86_64"
                    ),
                    events: [AnalyticsEntry.Event(id: 0, event: "backup_started")],
                    failures: [
                        AnalyticsEntry.Failure(
                            message: "connection refused",
                            timestamp: Date(timeIntervalSince1970: 1775809050)
                        )
                    ],
                    created: Date(timeIntervalSince1970: 1775808900),
                    updated: Date(timeIntervalSince1970: 1775809080)
                )
            )
        )
    }

    @Test("decode a request with a failure stack trace and re-encode it without one")
    func requestWithStackTrace() throws {
        let matches = CreateAnalyticsEntry(
            entry: AnalyticsEntry.AsJson(
                entryType: "collected",
                runtime: AnalyticsEntry.RuntimeInformation(
                    id: "10136ad4-c1c2-469b-94b9-79af1e2b939e",
                    app: "stasis-client;1.0.0",
                    jre: "none",
                    os: "linux;6.1.0;x86_64"
                ),
                events: [AnalyticsEntry.Event(id: 0, event: "backup_started")],
                failures: [
                    AnalyticsEntry.Failure(
                        message: "connection refused",
                        timestamp: Date(timeIntervalSince1970: 1775809050)
                    )
                ],
                created: Date(timeIntervalSince1970: 1775808900),
                updated: Date(timeIntervalSince1970: 1775809080)
            )
        )

        let input = InteropResources.load(domain: "analytics", resource: "CreateAnalyticsEntry.with-stack-trace")
        let decoded = try JSONCoders.decoder().decode(CreateAnalyticsEntry.self, from: input)
        #expect(decoded == matches)

        let expectedEncoded = InteropResources.load(domain: "analytics", resource: "CreateAnalyticsEntry")
        let actualEncoded = try JSONCoders.encoder().encode(matches)
        #expect(InteropResources.tree(actualEncoded) == InteropResources.tree(expectedEncoded))
    }

    @Test("decode and re-encode CreatedAnalyticsEntry")
    func createdAnalyticsEntry() throws {
        try assert(
            domain: "analytics",
            resource: "CreatedAnalyticsEntry",
            matches: CreatedAnalyticsEntry(
                entry: UUID(uuidString: "f2d2371a-55ea-452b-927b-a26d7e13d95e")!
            )
        )
    }
}

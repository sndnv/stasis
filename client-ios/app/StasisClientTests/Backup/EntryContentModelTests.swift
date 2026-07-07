import Foundation
@testable import StasisClient
import StasisClientLib
import Testing
import UIKit

@MainActor
@Suite("EntryContentModel")
struct EntryContentModelTests {
    private let recordKinds: [String: any LibraryRecordPreviewing] = [
        "test": LibraryRecordKind(
            source: FakeLibraryRecordSource(scheme: "test", records: [], readAccess: true, writeAccess: true)
        )
    ]

    private func libraryMetadata(key: String, size: Int64) -> EntityMetadata {
        .library(EntityMetadata.Library(
            path: key,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            size: size,
            checksum: Data(),
            crates: [:],
            compression: "none",
            attributes: Data()
        ))
    }

    private func directoryMetadata() -> EntityMetadata {
        .directory(EntityMetadata.Directory(
            path: "/tmp/test",
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            owner: "test",
            group: "test",
            permissions: "rwxr-xr-x"
        ))
    }

    private func model(
        entityKey: String,
        metadata: EntityMetadata,
        maxContentBytes: Int64 = 10_000,
        loadBytes: @escaping @Sendable (any EntityContentMetadata, String) async throws -> Data
    ) -> EntryContentModel {
        EntryContentModel(
            entityKey: entityKey,
            displayName: "test",
            metadata: metadata,
            recordKinds: recordKinds,
            maxContentBytes: maxContentBytes,
            loadBytes: loadBytes
        )
    }

    @Test("renders a record scheme as preview sections")
    func recordDispatchesToSections() async throws {
        let record = FakeLibraryRecordSource.Record(id: "id", name: "test", payload: "test a")
        let bytes = try JSONEncoder().encode(record)
        let subject = model(entityKey: "test:/id", metadata: libraryMetadata(key: "test:/id", size: Int64(bytes.count))) { _, _ in
            bytes
        }

        await subject.load()

        guard case .sections(let sections) = subject.state else {
            Issue.record("expected sections, got \(subject.state)")
            return
        }
        #expect(sections.first?.fields.contains(EntityPreview.Field(label: "Name", value: "test")) == true)
    }

    @Test("renders image bytes as an image")
    func imageDispatchesToImage() async {
        let png = imageData()
        let subject = model(entityKey: "photos:/id", metadata: libraryMetadata(key: "photos:/id", size: Int64(png.count))) { _, _ in
            png
        }

        await subject.load()

        #expect(subject.state == .image(png))
    }

    @Test("renders utf8 bytes as text")
    func textDispatchesToText() async {
        let bytes = Data("test a".utf8)
        let subject = model(entityKey: "photos:/id", metadata: libraryMetadata(key: "photos:/id", size: Int64(bytes.count))) { _, _ in
            bytes
        }

        await subject.load()

        #expect(subject.state == .text("test a"))
    }

    @Test("reports content over the size cap as too large")
    func oversizedReportsTooLarge() async {
        let subject = model(
            entityKey: "photos:/id",
            metadata: libraryMetadata(key: "photos:/id", size: 20_000),
            maxContentBytes: 10_000
        ) { _, _ in Data("test".utf8) }

        await subject.load()

        #expect(subject.state == .tooLarge(20_000))
    }

    @Test("reports directories without content as unavailable")
    func directoryReportsUnavailable() async {
        let subject = model(entityKey: "/tmp/test", metadata: directoryMetadata()) { _, _ in Data() }

        await subject.load()

        #expect(subject.state == .unavailable)
    }

    @Test("exposes export only for record schemes")
    func exportAvailabilityFollowsScheme() {
        let recordModel = model(entityKey: "test:/id", metadata: libraryMetadata(key: "test:/id", size: 1)) { _, _ in Data() }
        let photoModel = model(entityKey: "photos:/id", metadata: libraryMetadata(key: "photos:/id", size: 1)) { _, _ in Data() }

        #expect(recordModel.canExport)
        #expect(!photoModel.canExport)
    }

    private func imageData() -> Data {
        UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).pngData { context in
            UIColor.black.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        }
    }
}

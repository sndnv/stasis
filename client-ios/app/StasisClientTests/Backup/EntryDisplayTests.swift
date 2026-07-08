import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("EntryDisplay")
struct EntryDisplayTests {
    private func library(path: String, attributes: String) -> EntityMetadata {
        .library(EntityMetadata.Library(
            path: path,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            size: 1,
            checksum: Data([1]),
            crates: [:],
            compression: "none",
            attributes: Data(attributes.utf8)
        ))
    }

    private func file(path: String) -> EntityMetadata {
        .file(EntityMetadata.File(
            path: path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            owner: "test",
            group: "test",
            permissions: "rw-r--r--",
            size: 1,
            checksum: Data([1]),
            crates: [:],
            compression: "none"
        ))
    }

    private func directory(path: String) -> EntityMetadata {
        .directory(EntityMetadata.Directory(
            path: path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0),
            owner: "test",
            group: "test",
            permissions: "rwxr-xr-x"
        ))
    }

    @Test("decodes library attributes")
    func decodesAttributes() {
        #expect(
            EntryDisplay.attributes(library(path: "calendar:/uid-1", attributes: #"{"name":"test","calendar":"test a"}"#))
                == ["name": "test", "calendar": "test a"]
        )
    }

    @Test("returns no attributes for filesystem or missing metadata")
    func noAttributesForNonLibrary() {
        #expect(EntryDisplay.attributes(file(path: "/tmp/test")).isEmpty)
        #expect(EntryDisplay.attributes(nil).isEmpty)
    }

    @Test("returns no attributes for blank attributes")
    func noAttributesForBlank() {
        #expect(EntryDisplay.attributes(library(path: "calendar:/uid-1", attributes: "")).isEmpty)
    }

    @Test("uses the library name when present")
    func usesLibraryName() {
        #expect(
            EntryDisplay.displayName(path: "calendar:/uid-1", metadata: library(path: "calendar:/uid-1", attributes: #"{"name":"test"}"#))
                == "test"
        )
    }

    @Test("falls back to the last path component for a library without a name")
    func fallsBackToKeySegment() {
        #expect(
            EntryDisplay.displayName(path: "calendar:/uid-1", metadata: library(path: "calendar:/uid-1", attributes: "{}"))
                == "uid-1"
        )
    }

    @Test("uses the file name for filesystem metadata")
    func usesFileName() {
        #expect(EntryDisplay.displayName(path: "/tmp/test.pdf", metadata: file(path: "/tmp/test.pdf")) == "test.pdf")
    }

    @Test("labels each kind")
    func labelsKinds() {
        let event = library(path: "calendar:/uid-1", attributes: "{}")
        let person = library(path: "contacts:/uid-2", attributes: "{}")
        #expect(EntryDisplay.kindLabel(path: "calendar:/uid-1", metadata: event) == "Calendar event")
        #expect(EntryDisplay.kindLabel(path: "contacts:/uid-2", metadata: person) == "Contact")
        #expect(EntryDisplay.kindLabel(path: "/tmp/test.pdf", metadata: file(path: "/tmp/test.pdf")) == "File")
        #expect(EntryDisplay.kindLabel(path: "/tmp/test", metadata: directory(path: "/tmp/test")) == "Directory")
    }

    @Test("derives secondary text per kind")
    func derivesSecondary() {
        let named = library(path: "calendar:/uid-1", attributes: #"{"calendar":"test a"}"#)
        let unnamed = library(path: "calendar:/uid-1", attributes: "{}")
        let person = library(path: "contacts:/uid-2", attributes: "{}")
        let doc = file(path: "/tmp/nested/test.pdf")
        #expect(EntryDisplay.secondary(path: "calendar:/uid-1", metadata: named) == "Calendar · test a")
        #expect(EntryDisplay.secondary(path: "calendar:/uid-1", metadata: unnamed) == "Calendar event")
        #expect(EntryDisplay.secondary(path: "contacts:/uid-2", metadata: person) == "Contact")
        #expect(EntryDisplay.secondary(path: "/tmp/nested/test.pdf", metadata: doc) == "/tmp/nested")
    }

    @Test("picks an icon per kind")
    func picksIcon() {
        let event = library(path: "calendar:/uid-1", attributes: "{}")
        let person = library(path: "contacts:/uid-2", attributes: "{}")
        #expect(EntryDisplay.kindIcon(path: "calendar:/uid-1", metadata: event) == "calendar")
        #expect(EntryDisplay.kindIcon(path: "contacts:/uid-2", metadata: person) == "person.crop.circle")
        #expect(EntryDisplay.kindIcon(path: "/tmp/test.pdf", metadata: file(path: "/tmp/test.pdf")) == "doc")
        #expect(EntryDisplay.kindIcon(path: "/tmp/test", metadata: directory(path: "/tmp/test")) == "folder")
    }
}

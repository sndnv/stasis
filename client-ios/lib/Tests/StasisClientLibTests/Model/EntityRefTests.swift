import Foundation
@testable import StasisClientLib
import Testing

@Suite("EntityRef")
struct EntityRefTests {
    @Test("provides a filesystem key as an absolute path")
    func filesystemKey() {
        #expect(EntityRef.filesystem(URL(fileURLWithPath: "/tmp/a/b")).key == "/tmp/a/b")
    }

    @Test("maps a filesystem path while remaining a filesystem ref")
    func mapsFilesystem() {
        let ref = EntityRef.filesystem(URL(fileURLWithPath: "/tmp/a/b"))

        #expect(ref.mapFilesystem { $0.deletingLastPathComponent() }.key == "/tmp/a")
        #expect(ref.mapLibrary { scheme, path in (scheme, path) } == ref)
    }

    @Test("coerces a filesystem ref to a filesystem url")
    func filesystemAsFilesystem() throws {
        let url = URL(fileURLWithPath: "/tmp/a/b")

        #expect(try EntityRef.filesystem(url).asFilesystem() == url)
    }

    @Test("fails to coerce a filesystem ref to a library ref")
    func filesystemAsLibraryFails() {
        let ref = EntityRef.filesystem(URL(fileURLWithPath: "/tmp/a/b"))

        do {
            _ = try ref.asLibrary()
            Issue.record("expected InvalidArgumentError")
        } catch let error as InvalidArgumentError {
            #expect(error.message == "Requested a library reference but [/tmp/a/b] found")
        } catch {
            Issue.record("expected InvalidArgumentError but received [\(error)]")
        }
    }

    @Test("provides a library key as [scheme:path]")
    func libraryKey() {
        #expect(EntityRef.library(scheme: "photos", path: "/album/img.heic").key == "photos:/album/img.heic")
    }

    @Test("maps a library scheme and path while remaining a library ref")
    func mapsLibrary() {
        let ref = EntityRef.library(scheme: "photos", path: "/album/img.heic")

        #expect(ref.mapLibrary { scheme, path in (scheme, "\(path).bak") } == .library(scheme: "photos", path: "/album/img.heic.bak"))
        #expect(ref.mapFilesystem { $0.deletingLastPathComponent() } == ref)
    }

    @Test("coerces a library ref to a scheme and path")
    func libraryAsLibrary() throws {
        let (scheme, path) = try EntityRef.library(scheme: "photos", path: "/album/img.heic").asLibrary()

        #expect(scheme == "photos")
        #expect(path == "/album/img.heic")
    }

    @Test("fails to coerce a library ref to a filesystem ref")
    func libraryAsFilesystemFails() {
        let ref = EntityRef.library(scheme: "photos", path: "/album/img.heic")

        do {
            _ = try ref.asFilesystem()
            Issue.record("expected InvalidArgumentError")
        } catch let error as InvalidArgumentError {
            #expect(error.message == "Requested a filesystem reference but [photos:/album/img.heic] found")
        } catch {
            Issue.record("expected InvalidArgumentError but received [\(error)]")
        }
    }

    @Test("supports flat-mapping across kinds")
    func flatMapsAcrossKinds() {
        let filesystem = EntityRef.filesystem(URL(fileURLWithPath: "/tmp/a"))

        #expect(filesystem.flatMap { _ in .library(scheme: "photos", path: "/album") } == .library(scheme: "photos", path: "/album"))
    }

    @Test("is reconstructable from a filesystem key")
    func reconstructsFilesystem() {
        #expect(EntityRef.default(key: "/tmp/a/b") == .filesystem(URL(fileURLWithPath: "/tmp/a/b")))
    }

    @Test("is reconstructable from a library key")
    func reconstructsLibrary() {
        #expect(EntityRef.default(key: "photos:/album/img.heic") == .library(scheme: "photos", path: "/album/img.heic"))
    }

    @Test("round-trips a library key through [default] and [key]")
    func roundTripsLibraryKey() {
        let key = "photos:/album/img.heic"
        #expect(EntityRef.default(key: key).key == key)
    }
}

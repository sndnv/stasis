#if DEBUG
import Foundation
import StasisClientLib

enum MockLibraryFixtures {
    struct Fixture: Sendable {
        let path: String
        let crate: UUID
        let checksum: Data
        let state: FilesystemMetadata.EntityState
        let metadata: EntityMetadata
        let plaintext: Data

        var crateKey: String { "\(path)_0" }
    }

    static let all: [Fixture] = [textFile, imageFile, photo, contact, calendar, largeVideo, binary]

    static var contentEntities: [String: EntityMetadata] {
        Dictionary(uniqueKeysWithValues: all.map { ($0.path, $0.metadata) })
    }

    static var filesystemStates: [String: FilesystemMetadata.EntityState] {
        Dictionary(uniqueKeysWithValues: all.map { ($0.path, $0.state) })
    }

    static var crates: [UUID: Fixture] {
        Dictionary(uniqueKeysWithValues: all.map { ($0.crate, $0) })
    }

    private static let textFile = file(
        path: "/tmp/docs/test.txt",
        crate: UUID(uuidString: "aaa6ea07-41f3-41ff-9b3c-3f89e8e7fdd0")!,
        checksum: Data([2]),
        state: .new,
        plaintext: Data("test\ntest a\ntest b\n".utf8)
    )

    private static let imageFile = file(
        path: "/tmp/photos/test.png",
        crate: UUID(uuidString: "9c064d89-c64d-40e3-9800-38d88edd6b4c")!,
        checksum: Data([3]),
        state: .new,
        plaintext: pngData
    )

    private static let photo = library(
        path: "photos:/test",
        crate: UUID(uuidString: "b8579fda-3ab7-469d-81fb-9a6db36b016a")!,
        checksum: Data([4]),
        state: .new,
        attributes: #"{"name":"test.png"}"#,
        plaintext: pngData
    )

    private static let contact = library(
        path: "contacts:/test-a",
        crate: UUID(uuidString: "11de77fd-305d-434f-86f2-2e11668c58db")!,
        checksum: Data([5]),
        state: .updated,
        attributes: #"{"name":"test a"}"#,
        plaintext: encode(sampleContact)
    )

    private static let calendar = library(
        path: "calendar:/test-event",
        crate: UUID(uuidString: "24b4a0d2-20be-4f76-8e55-ef596b0fb894")!,
        checksum: Data([6]),
        state: .new,
        attributes: #"{"name":"test","calendar":"test a"}"#,
        plaintext: encode(sampleEvent)
    )

    private static let largeVideo = oversized(
        path: "/tmp/photos/test.mp4",
        crate: UUID(uuidString: "69ceb00f-ca20-4252-a001-b199e2f01e33")!,
        checksum: Data([7]),
        state: .new,
        declaredSize: 25 * 1024 * 1024
    )

    private static let binary = file(
        path: "/tmp/misc/test.bin",
        crate: UUID(uuidString: "d701b68a-7595-4ae3-bcc0-cfe08c0e722c")!,
        checksum: Data([8]),
        state: .new,
        plaintext: Data(repeating: 0xFF, count: 48)
    )

    private static func file(
        path: String,
        crate: UUID,
        checksum: Data,
        state: FilesystemMetadata.EntityState,
        plaintext: Data
    ) -> Fixture {
        let dates = MockServerApiEndpointClient.pastDates()
        let metadata = EntityMetadata.file(.init(
            path: path, link: nil, isHidden: false,
            created: dates.created, updated: dates.updated,
            owner: "root", group: "root", permissions: "rw-r--r--",
            size: Int64(plaintext.count), checksum: checksum,
            crates: ["\(path)_0": crate], compression: "none"
        ))
        return Fixture(path: path, crate: crate, checksum: checksum, state: state, metadata: metadata, plaintext: plaintext)
    }

    private static func oversized(
        path: String,
        crate: UUID,
        checksum: Data,
        state: FilesystemMetadata.EntityState,
        declaredSize: Int64
    ) -> Fixture {
        let dates = MockServerApiEndpointClient.pastDates()
        let metadata = EntityMetadata.file(.init(
            path: path, link: nil, isHidden: false,
            created: dates.created, updated: dates.updated,
            owner: "root", group: "root", permissions: "rw-r--r--",
            size: declaredSize, checksum: checksum,
            crates: ["\(path)_0": crate], compression: "none"
        ))
        return Fixture(path: path, crate: crate, checksum: checksum, state: state, metadata: metadata, plaintext: Data())
    }

    private static func library(
        path: String,
        crate: UUID,
        checksum: Data,
        state: FilesystemMetadata.EntityState,
        attributes: String,
        plaintext: Data
    ) -> Fixture {
        let dates = MockServerApiEndpointClient.pastDates()
        let metadata = EntityMetadata.library(.init(
            path: path, created: dates.created, updated: dates.updated,
            size: Int64(plaintext.count), checksum: checksum,
            crates: ["\(path)_0": crate], compression: "none",
            attributes: Data(attributes.utf8)
        ))
        return Fixture(path: path, crate: crate, checksum: checksum, state: state, metadata: metadata, plaintext: plaintext)
    }

    private static func encode<Value: Encodable>(_ value: Value) -> Data {
        (try? JSONEncoder().encode(value)) ?? Data()
    }

    private static let pngData = Data(base64Encoded:
        "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAATUlEQVR42u3YQQ0AIAwEwUqqdBRgraig"
        + "KWQuWQHzvci16+UCAAAAAAAAAKARcHsAAAAAAAAAAAAAAAAAAAAAADMBnjkAAAAAAACAjwAH/M2cR+mp"
        + "whwAAAAASUVORK5CYII="
    )!

    private static let sampleContact = ContactRecord(
        contactType: .person,
        namePrefix: "",
        givenName: "test",
        phoneticGivenName: "",
        middleName: "",
        phoneticMiddleName: "",
        familyName: "a",
        phoneticFamilyName: "",
        nameSuffix: "",
        nickname: "",
        organizationName: "test org",
        phoneticOrganizationName: "",
        departmentName: "",
        jobTitle: "test title",
        phoneNumbers: [.init(label: "mobile", value: "+1 555 0100")],
        emailAddresses: [.init(label: "home", value: "test@example.com")],
        urlAddresses: [],
        postalAddresses: [.init(
            label: "home", street: "1 test street", subLocality: "",
            city: "test city", subAdministrativeArea: "", state: "",
            postalCode: "00000", country: "test"
        )],
        instantMessageAddresses: [],
        socialProfiles: [],
        relations: [],
        dates: [],
        birthday: .init(year: 2000, month: 1, day: 2),
        imageData: nil
    )

    private static let sampleEvent = CalendarEvent(
        calendar: "test a",
        title: "test",
        notes: "test note",
        location: "test city",
        start: 1_700_000_000,
        end: 1_700_003_600,
        isAllDay: false,
        timeZone: "UTC",
        url: nil,
        availability: .busy,
        alarms: [.init(relativeOffset: -900, absoluteDate: nil)],
        recurrenceRules: []
    )
}
#endif
